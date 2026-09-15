package com.perioperative.planner

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Book(val cases: List<Case>, val selected: String)
class Storage(context: Context) {
    private val disk = AtomicFile(File(context.filesDir,"cases-v1.enc"))
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(ks.containsAlias("perioperative-v1")) return ks.getKey("perioperative-v1",null) as SecretKey
        return KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("perioperative-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): Book {
        if(!disk.baseFile.exists() && !File(disk.baseFile.path+".bak").exists()) {
            val c=Case();return Book(listOf(c),c.id)
        }
        val b=disk.readFully();require(b.size>13 && b[0].toInt()==1)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(1,13)))
        val root=JSONObject(String(cipher.doFinal(b.copyOfRange(13,b.size)),Charsets.UTF_8))
        require(root.getInt("schema")==1)
        val array=root.getJSONArray("cases")
        val cases=(0 until array.length()).map { i ->
            val o=array.getJSONObject(i);val fields=o.getJSONObject("fields")
            val checks=o.getJSONArray("checks");val actions=o.getJSONArray("actions")
            Case(o.getString("id"),fields.keys().asSequence().associateWith{fields.getString(it)},
                (0 until checks.length()).map{checks.getString(it)}.toSet(),
                (0 until actions.length()).map { j ->
                    val a=actions.getJSONObject(j)
                    Action(a.getString("id"),a.getString("rule"),a.getString("title"),a.getString("text"),
                        a.getString("source"),a.getInt("revision"),a.optString("owner"),a.optString("due"),
                        a.optString("trigger"),a.optString("missing"))
                },o.getInt("revision"),o.optBoolean("reviewed",false))
        }
        require(cases.isNotEmpty())
        return Book(cases,root.getString("selected").takeIf{id->cases.any{it.id==id}} ?: cases.first().id)
    }
    fun write(book: Book) {
        val array=JSONArray()
        book.cases.forEach { c ->
            val actions=JSONArray()
            c.actions.forEach{a->actions.put(JSONObject().put("id",a.id).put("rule",a.rule).put("title",a.title)
                .put("text",a.text).put("source",a.source).put("revision",a.revision).put("owner",a.owner).put("due",a.due)
                .put("trigger",a.trigger).put("missing",a.missing))}
            array.put(JSONObject().put("id",c.id).put("fields",JSONObject(c.fields))
                .put("checks",JSONArray(c.checks.toList())).put("actions",actions).put("revision",c.revision).put("reviewed",c.reviewed))
        }
        val root=JSONObject().put("schema",1).put("selected",book.selected).put("cases",array)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        require(cipher.iv.size==12)
        val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        val stream=disk.startWrite()
        try {stream.write(bytes);disk.finishWrite(stream)} catch(e:Exception){disk.failWrite(stream);throw e}
    }
}
class PlannerVM(app: Application):AndroidViewModel(app) {
    private val disk=Storage(app)
    var cases by mutableStateOf(listOf(Case()));private set
    var selected by mutableStateOf(cases.first().id);private set
    var status by mutableStateOf("Loading");private set
    var loadFailed by mutableStateOf(false);private set
    private val saves=Channel<Book>(Channel.CONFLATED)
    val c get()=cases.first{it.id==selected}
    init {
        reload()
        viewModelScope.launch {
            for(book in saves) {
                try {withContext(Dispatchers.IO){disk.write(book)}
                    status=if(book.cases==cases && book.selected==selected) "Saved on device" else "Saving…"
                } catch(_:Exception){status="Save failed — retry"}
            }
        }
    }
    fun reload() {
        try {val b=disk.read();cases=b.cases;selected=b.selected;loadFailed=false;status="Saved on device"}
        catch(_:Exception){loadFailed=true;status="Could not open saved cases. Existing data was preserved."}
    }
    fun save(){if(!loadFailed){status="Saving…";saves.trySend(Book(cases,selected))}}
    private fun replace(next:Case){cases=cases.map{if(it.id==next.id)next else it};save()}
    fun set(k:String,v:String){replace(c.set(k,v))}
    fun select(id:String){if(cases.any{it.id==id}){selected=id;save()}}
    fun newCase(demo:Boolean=false){
        val n=if(demo)Case(fields=mapOf("label" to "Fictional TKR","procedure" to "Knee replacement","urgency" to "Elective",
            "duration" to "120","position" to "Supine","age" to "68","height" to "165","weight" to "92","pbwSex" to "Female",
            "diabetes" to "Yes","osa" to "Yes","airway.opening" to "2","airway.mallampati" to "III","diabetes.a1c" to "8.6"))else Case()
        cases=cases+n;selected=n.id;save()
    }
    fun check(key:String){replace(c.copy(checks=if(key in c.checks)c.checks-key else c.checks+key,reviewed=false))}
    fun add(a:Advice){if(c.actions.none{it.rule==a.id})replace(c.copy(actions=c.actions+Action(rule=a.id,title=a.title,text=a.text,source=a.source,revision=c.revision,trigger=a.trigger,missing=a.missing),reviewed=false))}
    fun update(a:Action){replace(c.copy(actions=c.actions.map{if(it.id==a.id)a else it},reviewed=false))}
    fun remove(id:String){replace(c.copy(actions=c.actions.filterNot{it.id==id},reviewed=false))}
    fun review(){replace(c.copy(reviewed=true))}
}
