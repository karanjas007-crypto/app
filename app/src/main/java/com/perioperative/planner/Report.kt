package com.perioperative.planner

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Instant

object Report {
    fun text(c:Case)=buildString{
        appendLine("PERIOPERATIVE PLANNER")
        appendLine("Clinical preview · Editable draft for clinician review")
        appendLine("Generated: "+Instant.now())
        appendLine("Case: "+c.v("label").ifBlank{c.id.take(6)}+" · Revision "+c.revision)
        appendLine("Reviewing clinician: "+c.display("plan.clinician"))
        appendLine("Status: "+if(c.reviewed)"Reviewed draft"else"Review pending")
        appendLine("\nCASE")
        val labels=linkedMapOf("procedure" to "Operation","operation" to "Approach/incision","urgency" to "Urgency",
            "duration" to "Duration (minutes)","position" to "Position","tourniquet" to "Tourniquet",
            "age" to "Age","height" to "Height (cm)","weight" to "Weight (kg)","allergies" to "Allergies","preferences" to "History/preferences")
        labels.forEach{(key,label)->appendLine("$label: "+c.display(key))}
        appendLine("\nRISK ASSESSMENT")
        appendLine("ASA: "+c.display("asa")+"; emergency modifier: "+c.display("asa.emergency"))
        appendLine("RCRI: "+if(Engine.rcriEligible(c))Engine.score(c,Engine.rcri).text else "Case incomplete / outside implemented scope")
        appendLine("Functional capacity: "+c.display("cardiac.capacity")+"; DASI: "+c.display("cardiac.dasi"))
        appendLine("STOP-Bang: "+Engine.score(c,Engine.stop).text+"; "+Engine.osa(c))
        appendLine("No individual mortality, morbidity or ICU probability is assigned.")
        appendLine("\nKEY FINDINGS")
        val findings=linkedMapOf("airway.opening" to "Mouth opening (cm)","airway.mallampati" to "Mallampati",
            "airway.neck" to "Neck movement","airway.previous" to "Previous difficult airway","airway.aspiration" to "Aspiration risk",
            "airway.ventilation" to "Expected ventilation difficulty","airway.fasting" to "Fasting/intake",
            "resp.pap" to "Prescribed PAP","resp.oxygen" to "Baseline respiratory support","diabetes.glucose" to "Glucose (mg/dL)",
            "diabetes.a1c" to "HbA1c (%)","diabetes.meds" to "Diabetes treatment","medications" to "Medication list",
            "medicationPlan" to "Medication decisions","hb" to "Hemoglobin/FBC","labs" to "Other results")
        findings.forEach{(key,label)->appendLine("$label: "+c.display(key))}
        appendLine("\nGENERATED RECOMMENDATIONS")
        listOf(DecisionSupport.airway(c),DecisionSupport.extubation(c),DecisionSupport.analgesia(c)).forEach{d->
            appendLine("\n"+d.title+": "+d.recommendation)
            d.reasons.forEach{appendLine("Finding: $it")}
            d.actions.forEach{appendLine("• $it")}
            if(d.missing.isNotEmpty())appendLine("Complete / verify: "+d.missing.joinToString("; "))
            d.sources.forEach{key->val s=Sources.get(key);appendLine("Source: ${s.name}, ${s.edition} · ${s.url}")}
        }
        appendLine("\nINDIVIDUAL PLAN")
        appendLine("Anesthesia: "+c.display("plan.anesthesia"));appendLine("Airway: "+c.display("plan.airway"))
        planFields.forEach{(key,label)->appendLine("$label: "+c.display(key))}
        val regional=linkedMapOf("technique" to "Technique","coverage" to "Required coverage","assessment" to "Assessment",
            "route" to "Administration route","drug" to "Drug/formulation","dose" to "Entered dose (mg)",
            "concentration" to "Concentration (mg/mL)","baricity" to "Baricity","reference" to "Formulary verification",
            "adjuvants" to "Adjuvants and monitoring","total" to "Cumulative local anesthetic review")
        appendLine("\nREGIONAL PLAN")
        regional.forEach{(key,label)->appendLine("$label: "+c.display("plan.regional.$key"))}
        appendLine("\nACCEPTED ACTIONS")
        if(c.actions.isEmpty())appendLine("No recommendation cards added.")
        c.actions.forEach{a->
            appendLine("\n"+a.title+if(a.revision!=c.revision)" [FINDINGS CHANGED — REVIEW]"else"")
            appendLine(a.text);appendLine("Owner: "+a.owner.ifBlank{"Unassigned"}+"; due: "+a.due.ifBlank{"Not set"})
            appendLine("Trigger when added: "+a.trigger.ifBlank{"Not recorded"})
            appendLine("Missing when added: "+a.missing.ifBlank{"None listed; reassess current findings"})
            val source=Sources.get(a.source);appendLine("Source: "+source.name+", "+source.edition+" · "+source.url)
            appendLine("Content version: 17 Sep 2026; clinical review pending")
        }
        appendLine("\nSUGGESTIONS AWAITING A CLINICIAN DECISION")
        val pending=Content.advice(c).filter{r->c.actions.none{it.rule==r.id}}
        pending.forEach{appendLine("• "+it.title+" — trigger: "+it.trigger)}
        if(pending.isEmpty())appendLine("None generated; missing inputs can prevent recommendations.")
        if(c.v("anti.drug").isNotBlank()){
            appendLine("\nANTITHROMBOTIC TIMELINE")
            appendLine("Drug: "+c.display("anti.drug")+"; regimen: "+(Anticoagulation.selected(c)?.label?:"Not selected"))
            appendLine("Last dose: "+c.display("anti.last")+"; CrCl (mL/min): "+c.display("anti.crcl"))
            val t=Engine.timeline(c)
            t.steps.forEach{s->appendLine(s.title+": "+s.state+(s.earliest?.let{" — earliest reference "+Engine.stamp(it)}?:""));appendLine(s.detail)}
            appendLine("Missing / review: "+t.missing.joinToString("; "))
            appendLine("Time criteria do not establish clinical suitability.")
        }
        appendLine("\nOR PREPARATION")
        Content.prep(c.v("procedure")).forEach{item->appendLine((if(c.v("procedure")+"|"+item in c.checks)"[Reviewed] "else"[Pending] ")+item)}
        appendLine("Details: "+c.display("plan.prep"));appendLine("Blood: "+c.display("plan.blood"))
        appendLine("Owner/deadline: "+c.display("plan.prepOwner"))
        appendLine("\nRECOVERY / HANDOVER")
        appendLine("Destination: "+c.display("recovery.destination"))
        recoveryFields.forEach{(key,label)->appendLine("$label: "+c.display(key))}
        appendLine("\nUnanswered items remain unknown. No automated prescription or surgical clearance.")
    }
    fun pdf(text:String,output:OutputStream){
        val document=PdfDocument();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=10f}
        var number=1;var page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());var y=40f
        fun footer(){page.canvas.drawText("Perioperative Planner · Draft · Page $number",36f,818f,paint)}
        fun line(s:String){
            if(y>788f){footer();document.finishPage(page);number++;page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());y=40f}
            page.canvas.drawText(s,36f,y,paint);y+=15f
        }
        try{
            text.lines().forEach{paragraph->
                if(paragraph.isEmpty())line("")
                var rest=paragraph
                while(rest.isNotEmpty()){
                    var count=paint.breakText(rest,true,523f,null).coerceAtLeast(1)
                    if(count<rest.length){val space=rest.lastIndexOf(' ',count-1);if(space>count/2)count=space}
                    line(rest.take(count));rest=rest.drop(count).trimStart()
                }
            }
            footer();document.finishPage(page);document.writeTo(output)
        }finally{document.close()}
    }
}
