package com.perioperative.planner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun CaseScreen(vm:PlannerVM,next:()->Unit){
    Section("Procedure","Start with the planned operation"){
        Field(vm,"label","Non-identifying case label")
        Choice(vm,"procedure","Operation",Content.procedures)
        Field(vm,"operation","Incision / approach / surgery details",true)
        Choice(vm,"urgency","Urgency",listOf("Elective","Time-sensitive","Urgent","Emergency"))
        Field(vm,"duration","Expected duration (minutes)",min=1.0,max=1440.0)
        Choice(vm,"position","Position",listOf("Supine","Prone","Lateral","Lithotomy","Sitting","Other"))
        Field(vm,"tourniquet","Tourniquet site and duration")
        Choice(vm,"complexity","Surgical complexity (NICE context)",listOf("Minor","Intermediate","Major / complex"))
    }
    Section("Patient"){
        Field(vm,"age","Age (years; adult preview)",min=18.0,max=120.0)
        Field(vm,"height","Height (cm)",min=100.0,max=230.0)
        Field(vm,"weight","Weight (kg)",min=25.0,max=400.0)
        Choice(vm,"pbwSex","Sex coefficient for PBW",listOf("Female","Male"))
        Text("BMI: "+dec(Engine.bmi(vm.c))+" kg/m²")
        Field(vm,"allergies","Allergies and reactions (review before entering none)",true)
        Field(vm,"preferences","Prior anesthesia / patient preferences",true)
    }
    Section("Conditions","Unreviewed conditions remain unknown",false){
        Content.conditions.forEach{(key,label)->Tri(vm,key,label)}
    }
    Button(onClick=next,modifier=Modifier.fillMaxWidth()){Text("Continue assessment")}
}
@Composable fun Risk(c:Case){
    Info("ASA: "+c.display("asa")+"\nRCRI: "+(if(Engine.rcriEligible(c))Engine.score(c,Engine.rcri).text else "Case incomplete / outside implemented scope")+
        "\nFunctional capacity: "+c.display("cardiac.capacity"))
}
@Composable fun AssessmentScreen(vm:PlannerVM){
    val c=vm.c
    Risk(c)
    Section("Airway","Recommendations appear below the findings"){
        Field(vm,"airway.opening","Mouth opening (cm)",min=0.1,max=10.0)
        Choice(vm,"airway.mallampati","Mallampati",listOf("I","II","III","IV"))
        Choice(vm,"airway.neck","Neck movement",listOf("Normal","Restricted","Immobilized"))
        Field(vm,"airway.dentition","Dentition / loose teeth / dentures")
        Tri(vm,"airway.previous","Previous difficult airway")
        Field(vm,"airway.history","Previous devices and difficulty",true)
        Tri(vm,"airway.aspiration","Increased aspiration risk")
        Field(vm,"airway.fasting","Fasting / last oral intake / gastric concerns")
        Tri(vm,"airway.ventilation","Expected difficult mask or supraglottic ventilation")
        Tri(vm,"airway.apnea","Expected intolerance of brief apnea")
        Tri(vm,"airway.rescue","Expected difficult emergency invasive airway access")
        Recommendations(vm,"Airway")
    }
    Section("Cardiac assessment","Classification, function and RCRI stay separate",false){
        Choice(vm,"asa","Clinician-assigned ASA",listOf("I","II","III","IV","V","VI"))
        Tri(vm,"asa.emergency","ASA emergency modifier")
        Tri(vm,"cardiac.symptoms","New / active cardiac symptoms")
        Field(vm,"cardiac.findings","Symptoms, cardiac history, ECG/echo and specialist findings",true)
        Choice(vm,"cardiac.capacity","Functional capacity",listOf("Good (≥4 METs)","Poor (<4 METs)","Limited by noncardiac cause"))
        Field(vm,"cardiac.dasi","DASI score if obtained",min=0.0,max=58.2)
        val labels=listOf("High-risk surgery by RCRI definition","Ischemic heart disease","Heart failure",
            "Cerebrovascular disease","Insulin-treated diabetes","Creatinine >2 mg/dL")
        Engine.rcri.zip(labels).forEach{(key,label)->Tri(vm,key,label)}
        Info("RCRI surgical risk refers to intraperitoneal, intrathoracic or suprainguinal vascular surgery in the original model. No individual morbidity, mortality or ICU percentage is derived from ASA or METs.")
        Info("Testing depends on symptoms, procedural risk and function. Routine preoperative invasive coronary angiography is not recommended for noncardiac surgery.")
        SourceView("aha");Recommendations(vm,"Cardiac")
    }
    Section("OSA & respiratory",Engine.score(c,Engine.stop).text,false){
        Text(Engine.osa(c),fontWeight=FontWeight.Bold)
        val labels=listOf("Loud habitual snoring","Frequent daytime tiredness","Observed sleep apnea","Hypertension / treatment",
            "BMI >35 kg/m²","Age >50 years","Neck circumference >40 cm","Male criterion")
        Engine.stop.zip(labels).forEach{(key,label)->Tri(vm,key,label)}
        Info("Classic eight-factor screening. Confirm objective factors from the patient. Incomplete screening stays incomplete. Screening does not diagnose OSA or choose PAP settings.")
        SourceView("stop")
        Field(vm,"resp.pap","Existing prescribed PAP: settings, adherence and availability",true)
        Field(vm,"resp.oxygen","Baseline oxygen, saturation and respiratory support")
        Text("Adult height-based PBW: "+dec(Engine.pbw(c))+" kg")
        Field(vm,"resp.mlkg","Clinician-selected tidal volume factor (mL/kg PBW)",min=1.0,max=15.0)
        Text("Calculated tidal volume: "+dec(Engine.pbw(c)?.let{p->c.num("resp.mlkg",1.0,15.0)?.times(p)},0)+" mL")
        Info("PBW = sex coefficient + 0.91 × (height in cm − 152.4). This calculates the entered factor, not a prescribed setting. Assess mechanics, gas exchange and clinical context; no PEEP is selected.")
        SourceView("pbw");Recommendations(vm,"Respiratory")
    }
    if(c.yes("diabetes"))Section("Diabetes"){
        Choice(vm,"diabetes.type","Type",listOf("Type 1","Type 2","Gestational","Other"))
        Field(vm,"diabetes.glucose","Glucose (mg/dL)",min=10.0,max=1500.0)
        Field(vm,"diabetes.a1c","HbA1c (%)",min=3.0,max=25.0)
        Field(vm,"diabetes.a1cDate","HbA1c date")
        Field(vm,"diabetes.meds","Medication regimen / insulin / pump",true)
        Field(vm,"diabetes.complications","Complications / hypoglycemia history",true)
        Tri(vm,"diabetes.glp1","GLP-1 or dual GIP/GLP-1 treatment")
        if(c.yes("diabetes.glp1")){
            Field(vm,"diabetes.glp1Phase","Drug, dose, escalation phase and last dose",true)
            Field(vm,"diabetes.gi","GI symptoms and gastric-emptying concerns",true)
        }
        Tri(vm,"diabetes.sglt2","SGLT2 inhibitor")
        if(c.yes("diabetes.sglt2"))Field(vm,"diabetes.sglt2Last","Agent and last administration")
        Field(vm,"diabetes.monitor","Monitoring frequency / hypoglycemia protocol",true)
        Field(vm,"diabetes.protocol","Institutional insulin protocol name and version")
        Info("IV insulin doses are not generated until a named validated institutional protocol is implemented.",true)
        Recommendations(vm,"Diabetes")
    }
    if(c.yes("ph"))Section("Pulmonary hypertension"){
        Choice(vm,"ph.group","Classification",listOf("Group 1","Group 2","Group 3","Group 4","Group 5"))
        Field(vm,"ph.severity","Severity / functional class / hemodynamics")
        Field(vm,"ph.rv","Right-ventricular function and assessment date")
        Field(vm,"ph.treatment","Treatment, infusion support and specialist plan",true)
        Recommendations(vm,"Pulmonary hypertension")
    }
    val extra=mapOf(
        "pulmonary" to ("Pulmonary disease" to listOf("Diagnosis and control","Recent exacerbation / infection / admission","Inhalers, steroids and baseline oxygen")),
        "renal" to ("Renal disease" to listOf("Renal trend, electrolytes and dates","Dialysis, last session and volume status")),
        "liver" to ("Liver disease" to listOf("Synthetic function, INR, bilirubin and albumin","Portal hypertension, ascites and encephalopathy")),
        "mh" to ("MH susceptibility" to listOf("Personal/family history and testing","Machine preparation and response protocol")),
        "opioid" to ("Chronic opioid use" to listOf("Drug, dose, schedule and last administration","Prescriber, tolerance and pain plan")),
        "frailty" to ("Frailty" to listOf("Baseline mobility and assistance","Named validated frailty scale and result")),
        "anemia" to ("Anemia" to listOf("Hemoglobin, cause, symptoms and treatment","Blood preferences and optimization")),
        "nutrition" to ("Nutrition" to listOf("Weight change, intake and assessment","Nutrition-team plan")),
        "delirium" to ("Delirium" to listOf("Baseline cognition, prior delirium and sensory aids","Medication review and screening plan")),
        "ponv" to ("PONV" to listOf("Prior PONV/motion sickness and smoking","Opioid exposure and prophylaxis"))
    )
    extra.forEach{(key,detail)->if(c.yes(key))Section(detail.first,expanded=false){
        detail.second.forEachIndexed{i,label->Field(vm,"$key.detail$i",label,true)}
        Recommendations(vm,detail.first)
    }}
    Section("Medication reconciliation",expanded=false){
        Field(vm,"medications","Medicine, dose, indication and last dose",true)
        Field(vm,"medicationPlan","Continue / withhold / restart decisions and responsible clinician",true)
        Tri(vm,"medicationComplete","Medication reconciliation completed")
    }
    Section("Neuraxial & anticoagulant timeline","Separate insertion, removal and restart checks",false){AnticoagulantScreen(vm)}
    Section("Investigations",expanded=false){
        Field(vm,"hb","Hemoglobin / FBC and date")
        Field(vm,"labs","Other results with units and dates",true)
        Field(vm,"investigations","Requested investigations, indication and owner",true)
        Recommendations(vm,"Investigations")
    }
}
@Composable fun AnticoagulantScreen(vm:PlannerVM){
    val c=vm.c
    Choice(vm,"anti.drug","Medication",listOf("Apixaban","Rivaroxaban","Dabigatran","Edoxaban","Enoxaparin","UFH","Warfarin","Clopidogrel","Ticagrelor","Aspirin","Other"))
    Field(vm,"anti.dose","Actual dose, unit and frequency")
    Field(vm,"anti.indication","Indication and duration of treatment")
    Field(vm,"anti.crcl","Creatinine clearance (mL/min; not indexed eGFR)",min=1.0,max=200.0)
    Field(vm,"anti.labs","Relevant laboratory values, units and collection times",true)
    Tri(vm,"anti.other","Other antithrombotics / interacting treatment")
    Tri(vm,"anti.bleeding","Active bleeding / other hemostatic concern")
    Tri(vm,"anti.thrombosis","Thrombosis and interruption risk reviewed")
    Field(vm,"anti.thrombosisPlan","Thrombosis/interrupting-treatment plan and clinician",true)
    TimeField(vm,"anti.last","Last medication administration")
    if(c.v("anti.drug")=="Apixaban"){
        Choice(vm,"anti.class","ASRA dose class",listOf("Low","High"))
        Tri(vm,"anti.verified","Dose class verified against ASRA indication/dose/renal table")
        Info("Limited apixaban reference branches: CrCl ≥30 mL/min and no other antithrombotics or bleeding concern. Dose class is not inferred from milligrams alone. No assay-based exception or reversal strategy is implemented.",true)
    }else{
        Info("For this drug, enter intervals verified from its guideline/protocol. The app provides interval arithmetic.",true)
        Field(vm,"anti.source","Source, edition, section and review date",true)
        Field(vm,"anti.hold","Verified last-dose to insertion interval (whole h)",min=0.0,max=720.0)
        Field(vm,"anti.removeHours","Verified last-dose to catheter removal interval (whole h)",min=0.0,max=720.0)
        Field(vm,"anti.after","Verified event-to-subsequent-dose interval (whole h)",min=0.0,max=720.0)
        Tri(vm,"anti.manualVerified","Intervals and applicability verified by clinician")
    }
    TimeField(vm,"anti.insertion","Actual / proposed insertion")
    Tri(vm,"anti.catheter","Catheter present / planned")
    if(c.yes("anti.catheter")){
        Tri(vm,"anti.exposed","Medication given while catheter present")
        TimeField(vm,"anti.removal","Actual / proposed removal")
    }
    Tri(vm,"anti.traumatic","Traumatic puncture")
    Tri(vm,"anti.hemostasis","Adequate hemostasis confirmed for restart")
    TimeField(vm,"anti.surgical","Earliest restart allowed by surgical/bleeding plan")
    TimeField(vm,"anti.restart","Proposed subsequent administration")
    val t=Engine.timeline(c)
    t.steps.forEach{s->Section(s.title,s.state){
        s.earliest?.let{Text("Earliest reference: "+Engine.stamp(it))}
        if(s.detail.isNotBlank())Text(s.detail,style=MaterialTheme.typography.bodySmall)
    }}
    if(t.missing.isNotEmpty())Info("Missing / review required:\n"+t.missing.joinToString("\n"){ "• $it" },true)
    SourceView(t.source)
}
val planFields=linkedMapOf("plan.reason" to "Why this approach fits the patient and operation",
    "plan.oxygenation" to "Preparation, positioning and oxygenation","plan.rescue" to "Rescue, attempt limits and emergency access",
    "plan.extubation" to "Extubation and reintubation","plan.analgesia" to "Analgesia and rescue",
    "plan.monitoring" to "Monitoring, access and hemodynamic goals","plan.alternative" to "Alternatives / insufficient-block contingency",
    "plan.concerns" to "Unresolved concerns and responsible clinician")
val recoveryFields=linkedMapOf("recovery.reason" to "Destination reasoning and escalation criteria",
    "recovery.analgesia" to "Analgesia / rescue / opioid-sparing plan","recovery.resp" to "Respiratory support, prescribed PAP and monitoring",
    "recovery.block" to "Sensory/motor assessment, catheter and block follow-up","recovery.restart" to "Drug restart: time, criteria and owner",
    "recovery.glucose" to "Glucose, nutrition and hypoglycemia monitoring","recovery.mobility" to "Mobility, PONV and delirium plan",
    "recovery.tasks" to "Outstanding tasks and handover recipient")
@Composable fun PlanScreen(vm:PlannerVM,preview:()->Unit){
    val c=vm.c;Risk(c)
    Section("Individual perioperative plan"){
        Choice(vm,"plan.anesthesia","Anesthesia",listOf("General","Spinal","Epidural","Combined spinal–epidural","Peripheral regional","Combined technique","Other"))
        Choice(vm,"plan.airway","Primary airway approach",listOf("Awake flexible scope","Awake video-assisted","Video laryngoscopy","Direct laryngoscopy","Supraglottic airway","Regional with airway contingency","Other"))
        if(c.v("plan.airway")=="Supraglottic airway"&&(c.yes("airway.aspiration")||c.yes("airway.ventilation")))
            Info("Reassess the selection against the documented aspiration/ventilation concern; document reasoning and rescue.",true)
        planFields.forEach{(key,label)->Field(vm,key,label,true)}
    }
    Section("Regional-anesthesia planner",expanded=false){
        Text(Content.regional(c.v("procedure")))
        Field(vm,"plan.regional.technique","Selected block / side / catheter")
        Field(vm,"plan.regional.coverage","Required sensory coverage, incision and tourniquet",true)
        Field(vm,"plan.regional.assessment","Block assessment, timing and contingency",true)
        Choice(vm,"plan.regional.route","Administration route",listOf("Intrathecal","Epidural","Peripheral"))
        Field(vm,"plan.regional.drug","Drug and verified formulation")
        Field(vm,"plan.regional.dose","Clinician-selected dose (mg)",min=0.001,max=1000.0)
        Field(vm,"plan.regional.concentration","Concentration (mg/mL, not %)",min=0.001,max=100.0)
        Text("Arithmetic volume: "+dec(Engine.volume(c.num("plan.regional.dose",0.001,1000.0),c.num("plan.regional.concentration",0.001,100.0)),2)+" mL")
        Info("Volume = entered mg ÷ entered mg/mL. Input limits are data-entry limits, not safe dosing limits. No dose, maximum dose, duration or dermatome is prescribed.",true)
        Choice(vm,"plan.regional.baricity","Baricity",listOf("Hyperbaric","Isobaric","Hypobaric","Not applicable"))
        Field(vm,"plan.regional.reference","Route-specific formulary reference and maximum-dose review",true)
        Field(vm,"plan.regional.adjuvants","Adjuvants: route, dose, units, formulation and monitoring",true)
        Field(vm,"plan.regional.total","Cumulative local anesthetic from all routes and operators",true)
        Info("Spread depends on dose, baricity, position and anatomy. Confirm surgical block quality and have a backup for insufficient duration or coverage.")
        SourceView("regional")
    }
    Section("Actions added to plan",c.actions.size.toString()+" actions"){
        if(c.actions.isEmpty())Text("Add recommendations from Assessment, or document your own plan above.")
        c.actions.forEach{a->Section(a.title,if(a.revision!=c.revision)"Findings changed — review needed"else"Based on current assessment",false){
            Text("Trigger when added: "+a.trigger.ifBlank{"Not recorded"},style=MaterialTheme.typography.bodySmall)
            if(a.missing.isNotBlank())Info("Missing when added: "+a.missing,true)
            OutlinedTextField(a.text,{vm.update(a.copy(text=it))},label={Text("Editable action")},modifier=Modifier.fillMaxWidth(),minLines=3)
            OutlinedTextField(a.owner,{vm.update(a.copy(owner=it))},label={Text("Owner")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(a.due,{vm.update(a.copy(due=it))},label={Text("Due / review time")},modifier=Modifier.fillMaxWidth())
            SourceView(a.source)
            TextButton(onClick={vm.update(a.copy(revision=c.revision))}){Text("Reviewed against current findings")}
            TextButton(onClick={vm.remove(a.id)}){Text("Remove")}
        }}
    }
    Section("Review and handover"){
        val pending=Content.advice(c).count{r->c.actions.none{it.rule==r.id}}
        Info("$pending suggestions await a decision. Review allergies, missing assessments and unresolved plan items.")
        Field(vm,"plan.clinician","Reviewing anesthesiologist")
        Button(onClick=vm::review,enabled=c.v("plan.clinician").isNotBlank()){Text("Mark draft reviewed")}
        Text("Review status records a clinician review; it does not establish suitability for surgery.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=preview,modifier=Modifier.fillMaxWidth()){Text("Preview handover")}
    }
}
@Composable fun PrepScreen(vm:PlannerVM){
    val c=vm.c;val prefix=c.v("procedure");val items=Content.prep(prefix)
    Section("Operating-room checklist",items.count{"$prefix|$it" in c.checks}.toString()+" of "+items.size+" reviewed"){
        Text(prefix.ifBlank{"Select the operation for procedure-specific items."})
        items.forEach{item->Row(Modifier.fillMaxWidth()){
            Checkbox(checked="$prefix|$item" in c.checks,onCheckedChange={vm.check("$prefix|$item")})
            Text(item,modifier=Modifier.weight(1f).padding(top=12.dp))
        }}
        Info("Planning prompts; reviewed items do not automatically establish readiness.")
        SourceView("local")
    }
    Section("Preparation details"){
        Field(vm,"plan.prep","Equipment, medicines, access and investigations",true)
        Field(vm,"plan.blood","Blood availability and hemorrhage plan",true)
        Field(vm,"plan.prepOwner","Outstanding preparation: owner and deadline",true)
    }
}
@Composable fun RecoveryScreen(vm:PlannerVM,preview:()->Unit){
    Section("Recovery and handover"){
        Choice(vm,"recovery.destination","Destination",listOf("PACU","Ward","HDU / step-down","ICU","Other"))
        recoveryFields.forEach{(key,label)->Field(vm,key,label,true)}
        Button(onClick=preview,modifier=Modifier.fillMaxWidth()){Text("Preview handover")}
    }
}
