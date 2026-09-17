package com.perioperative.planner

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val teal=Color(0xFF087F8C)
val navy=Color(0xFF132D40)
class MainActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){
        super.onCreate(state);enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme=lightColorScheme(primary=teal,onPrimary=Color.White,
            primaryContainer=Color(0xFFD8F0F1),background=Color(0xFFF4F7F8),surface=Color.White,onSurface=navy)){
            App()
        }}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm:PlannerVM=viewModel()){
    var advisor by rememberSaveable{mutableStateOf(true)}
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var caseList by rememberSaveable{mutableStateOf(false)}
    var preview by rememberSaveable{mutableStateOf(false)}
    var export by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        if(uri!=null)try{
            context.contentResolver.openOutputStream(uri)?.use{Report.pdf(export,it)}?:error("Destination unavailable")
            Toast.makeText(context,"PDF exported",Toast.LENGTH_SHORT).show()
        }catch(_:Exception){Toast.makeText(context,"Export failed",Toast.LENGTH_LONG).show()}
    }
    val txt=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->
        if(uri!=null)try{
            context.contentResolver.openOutputStream(uri)?.use{it.write(export.toByteArray(Charsets.UTF_8))}?:error("Destination unavailable")
            Toast.makeText(context,"Text exported",Toast.LENGTH_SHORT).show()
        }catch(_:Exception){Toast.makeText(context,"Export failed",Toast.LENGTH_LONG).show()}
    }
    if(vm.loadFailed){
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
            Text(vm.status);Button(onClick=vm::reload){Text("Retry opening cases")}
        };return
    }
    if(advisor){AdvisorHome(vm){advisor=false};return}
    val c=vm.c
    val names=listOf("Case","Assessment","Anesthesia plan","OR preparation","Recovery")
    val short=listOf("Case","Assess","Plan","OR prep","Recovery")
    val icons=listOf(Icons.Outlined.Description,Icons.Outlined.Search,Icons.Outlined.Assignment,Icons.Outlined.Checklist,Icons.Outlined.Bed)
    Scaffold(topBar={TopAppBar(title={Column{
        Text("PERIOPERATIVE PLANNER",fontSize=10.sp,color=teal,letterSpacing=1.sp)
        Text(names[tab],style=MaterialTheme.typography.titleLarge)
    }},actions={
        TextButton(onClick={advisor=true}){Text("Advisor")}
        TextButton(onClick={caseList=true}){Text("Cases")}
        IconButton(onClick=vm::save){Icon(Icons.Outlined.Save,"Save case")}
    })},bottomBar={NavigationBar{
        short.forEachIndexed{i,s->NavigationBarItem(selected=i==tab,onClick={tab=i},
            icon={Icon(icons[i],null)},label={Text(s,fontSize=11.sp)},modifier=Modifier.testTag("nav_$i"))}
    }}){pad->
        Column(Modifier.padding(pad).fillMaxSize()){
            Row(Modifier.fillMaxWidth().background(Color(0xFFE7EFF2)).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Column(Modifier.weight(1f)){
                    Text(c.v("label").ifBlank{"Case "+c.id.take(6)},fontWeight=FontWeight.SemiBold)
                    Text(c.v("procedure").ifBlank{"Procedure not selected"},fontSize=12.sp)
                }
                Text(if(c.reviewed)"Reviewed draft" else "Draft · r"+c.revision,fontSize=12.sp,color=teal)
            }
            Text("Clinical preview · verify recommendations with local practice",fontSize=11.sp,color=Color(0xFF825500),
                modifier=Modifier.fillMaxWidth().background(Color(0xFFFFF5DE)).padding(8.dp))
            key(c.id,tab){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)){
                when(tab){0->CaseScreen(vm){tab=1};1->AssessmentScreen(vm);2->PlanScreen(vm){preview=true};3->PrepScreen(vm);4->RecoveryScreen(vm){preview=true}}
                Text(vm.status,fontSize=11.sp,color=Color.Gray)
                if(vm.status.startsWith("Save failed"))TextButton(onClick=vm::save){Text("Retry save")}
                Spacer(Modifier.height(12.dp))
            }}
        }
    }
    if(caseList)AlertDialog(onDismissRequest={caseList=false},title={Text("Your cases")},
        text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){
            vm.cases.forEach{saved->TextButton(onClick={vm.select(saved.id);caseList=false;tab=0}){
                Text(saved.v("label").ifBlank{saved.id.take(6)}+" · "+saved.v("procedure").ifBlank{"New case"})
            }}
        }},confirmButton={TextButton(onClick={vm.newCase();caseList=false;tab=0}){Text("New case")}},
        dismissButton={TextButton(onClick={vm.newCase(true);caseList=false;tab=0}){Text("Add demo")}})
    if(preview)AlertDialog(onDismissRequest={preview=false},title={Text("Handover preview")},
        text={Text(Report.text(c),style=MaterialTheme.typography.bodySmall,modifier=Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={export=Report.text(c);pdf.launch("Perioperative-plan-"+c.id.take(6)+".pdf")}){Text("Export PDF")}},
        dismissButton={Row{
            TextButton(onClick={export=Report.text(c);txt.launch("Perioperative-plan-"+c.id.take(6)+".txt")}){Text("Text")}
            TextButton(onClick={preview=false}){Text("Close")}
        }})
}
@Composable
fun Section(title:String,subtitle:String="",expanded:Boolean=true,content:@Composable ColumnScope.()->Unit){
    var open by rememberSaveable(title){mutableStateOf(expanded)}
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth().clickable{open=!open},verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                if(subtitle.isNotBlank())Text(subtitle,fontSize=12.sp,color=teal)}
            Icon(if(open)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(open)"Collapse" else "Expand")
        }
        if(open)content()
    }}
}
@Composable
fun Field(vm:PlannerVM,k:String,label:String,multi:Boolean=false,min:Double?=null,max:Double?=null){
    val s=vm.c.v(k);val n=s.toDoubleOrNull()
    val bad=s.isNotBlank() && min!=null && (n==null || !n.isFinite() || n<min || max!=null && n>max)
    OutlinedTextField(value=s,onValueChange={vm.set(k,it)},label={Text(label)},modifier=Modifier.fillMaxWidth().testTag(k),
        singleLine=!multi,minLines=if(multi)2 else 1,isError=bad,
        keyboardOptions=KeyboardOptions(keyboardType=if(min!=null)KeyboardType.Decimal else KeyboardType.Text),
        supportingText=if(bad)({Text("Enter a valid value in the stated units.")})else null)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Choice(vm:PlannerVM,k:String,label:String,items:List<String>){
    Column{Text(label,style=MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            (listOf("Unknown")+items).forEach{labelValue->
                val value=if(labelValue=="Unknown")"" else labelValue
                FilterChip(selected=vm.c.v(k)==value,onClick={vm.set(k,value)},label={Text(labelValue)},modifier=Modifier.testTag("$k:$value"))
            }
        }
    }
}
@Composable fun Tri(vm:PlannerVM,k:String,label:String)=Choice(vm,k,label,listOf("Yes","No"))
@Composable fun Info(text:String,warn:Boolean=false){
    Surface(color=if(warn)Color(0xFFFFF1D4)else Color(0xFFE8F4F5),shape=MaterialTheme.shapes.small,modifier=Modifier.fillMaxWidth()){
        Text(text,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp),color=if(warn)Color(0xFF825500)else navy)
    }
}
@Composable fun SourceView(id:String){
    val source=Sources.get(id);val context=LocalContext.current
    Text(source.name+" · "+source.edition,fontSize=11.sp,color=teal)
    Text("Content version: 17 Sep 2026 · Clinical review pending",fontSize=10.sp,color=Color.Gray)
    if(source.url.isNotBlank())TextButton(onClick={
        try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source.url)))}catch(_:Exception){Toast.makeText(context,"No browser available",Toast.LENGTH_SHORT).show()}
    }){Text("View source")}
}
@Composable fun Recommendations(vm:PlannerVM,section:String){
    Content.advice(vm.c).filter{it.section==section}.forEach{a->
        Section(a.title,"Trigger: "+a.trigger,expanded=true){
            Text(a.text,style=MaterialTheme.typography.bodySmall)
            if(a.missing.isNotBlank())Info("Missing: "+a.missing,true)
            SourceView(a.source)
            val added=vm.c.actions.any{it.rule==a.id}
            Button(onClick={vm.add(a)},enabled=!added){Text(if(added)"Added to plan"else"Add to plan")}
        }
    }
}
@Composable fun TimeField(vm:PlannerVM,k:String,label:String){
    val ctx=LocalContext.current;val s=vm.c.v(k)
    OutlinedTextField(s,{vm.set(k,it)},label={Text(label)},modifier=Modifier.fillMaxWidth().testTag(k),singleLine=true,
        isError=s.isNotBlank()&&Engine.time(s)==null,placeholder={Text("2026-09-15T08:00-07:00",fontSize=11.sp)},
        supportingText={Text("Date, time and UTC offset required")})
    TextButton(onClick={
        val zone=ZoneId.systemDefault();val start=Engine.time(s)?.atZone(zone)?.toLocalDateTime()?:LocalDateTime.now()
        DatePickerDialog(ctx,{_,y,m,d->TimePickerDialog(ctx,{_,h,min->
            vm.set(k,LocalDateTime.of(y,m+1,d,h,min).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        },start.hour,start.minute,true).show()},start.year,start.monthValue-1,start.dayOfMonth).show()
    }){Text("Choose date/time · "+ZoneId.systemDefault().id)}
}
fun dec(n:Double?,places:Int=1)=n?.let{String.format(Locale.US,"%."+places+"f",it)}?:"Unknown"
