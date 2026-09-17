package com.perioperative.planner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun AdvisorHome(vm:PlannerVM,onWorkspace:()->Unit){
    var module by rememberSaveable{mutableIntStateOf(0)}
    var results by rememberSaveable{mutableStateOf(false)}
    val labels=listOf("Neuraxial","Airway","Extubation","Analgesia")
    Scaffold(topBar={TopAppBar(title={Text("Anesthesia advisor")},actions={
        TextButton(onClick={vm.newAdvisorExample();results=true}){Text("Example")}
        TextButton(onClick=onWorkspace){Text("Full planner")}
    })},bottomBar={Surface(tonalElevation=3.dp){Column(Modifier.navigationBarsPadding().padding(12.dp)){
        Button(onClick={results=!results},modifier=Modifier.fillMaxWidth().testTag("advisor_toggle")){
            Text(if(results)"Edit inputs" else "Show recommendations")
        }
    }}}){padding->
        Column(Modifier.padding(padding).fillMaxSize()){
            FlowRow(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                labels.forEachIndexed{i,s->FilterChip(module==i,{module=i;results=false},label={Text(s)},modifier=Modifier.testTag("advisor_$i"))}
            }
            key(vm.c.id,module,results){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text(if(results)"Recommendations from your inputs"else"${labels[module]} · patient inputs",style=MaterialTheme.typography.headlineSmall)
                Text("Adult clinician decision support · development preview",style=MaterialTheme.typography.labelLarge,color=teal)
                if(vm.c.v("label").isNotBlank())Text(vm.c.v("label"),style=MaterialTheme.typography.bodySmall)
                if(results){
                    when(module){
                        0->NeuraxialResults(vm.c)
                        1->DecisionCard(DecisionSupport.airway(vm.c))
                        2->DecisionCard(DecisionSupport.extubation(vm.c))
                        else->DecisionCard(DecisionSupport.analgesia(vm.c))
                    }
                    Info("For evaluation with fictional cases until independently clinically validated. Apply bedside assessment and current local policy; a timing result does not establish that a procedure is safe.")
                }else when(module){
                    0->NeuraxialInputs(vm)
                    1->AirwayInputs(vm)
                    2->ExtubationInputs(vm)
                    else->AnalgesiaInputs(vm)
                }
                Text(vm.status,style=MaterialTheme.typography.bodySmall)
            }}
        }
    }
}

@Composable fun DecisionCard(d:Decision){
    Section(d.title){
        Text(d.recommendation,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        if(d.missing.isNotEmpty())Info("Complete / verify: "+d.missing.joinToString("; "),true)
        if(d.reasons.isNotEmpty()){
            Text("Why this recommendation",fontWeight=FontWeight.Bold)
            d.reasons.forEach{Text("• $it")}
        }
        Text("Suggested approach",fontWeight=FontWeight.Bold)
        d.actions.forEach{Text("• $it")}
        Text("Clinical synthesis of the entered findings and linked guidance. Apply clinical judgment; the app has not undergone independent clinical validation.",style=MaterialTheme.typography.bodySmall)
        d.sources.forEach{SourceView(it)}
    }
}

@Composable fun NeuraxialResults(c:Case){
    val t=Engine.timeline(c);val r=Anticoagulation.selected(c)
    r?.let{Info(it.drug+" · "+it.label)}
    if(c.v("anti.last").isNotBlank())Text("Last administration: "+c.v("anti.last"))
    t.steps.forEach{s->Section(s.title,s.state){
        s.earliest?.let{Text(Engine.stamp(it),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(s.detail.isNotBlank())Text(s.detail)
    }}
    if(t.missing.isNotEmpty())Section("Missing information / exceptions"){
        t.missing.forEach{Text("• $it")}
    }
    if(r!=null)Info(if(r.maintenance)"This regimen can permit catheter maintenance under its drug-specific conditions; the restart result above calculates the scenario after removal."
        else "Remove the catheter before planned resumption under this pathway. Unexpected dosing with a catheter needs the separate exposure assessment.")
    SourceView("asra")
}

@Composable fun NeuraxialInputs(vm:PlannerVM){
    val c=vm.c;val r=Anticoagulation.selected(c)
    Section("Medication and last dose"){
        Field(vm,"age","Age (adult years)",min=18.0,max=120.0)
        Choice(vm,"anti.drug","Blood thinner",Anticoagulation.drugs)
        RegimenPicker(vm)
        TimeField(vm,"anti.last",if(r?.id=="ufh_iv")"When was the infusion stopped?"else"When was the last dose given?")
        Field(vm,"anti.dose","Additional dose / indication details (optional)")
        if(r?.family in listOf("DOAC","LMWH")){
            Field(vm,"anti.crcl","Creatinine clearance (mL/min)",min=1.0,max=200.0)
            Tri(vm,"anti.aki","Acute kidney injury / unstable renal function")
        }
        Tri(vm,"anti.other","Any other antithrombotic or interacting drug?")
        Tri(vm,"anti.bleeding","Active bleeding, thrombocytopenia or other hemostatic concern?")
        if(r?.family in listOf("UFH","LMWH")){
            Tri(vm,"anti.over4days","Heparin / LMWH for more than four days?")
            if(c.yes("anti.over4days"))Tri(vm,"anti.platelets","Current platelet count / HIT assessment acceptable?")
        }
        if(r?.family=="UFH" && r.id!="ufh_low")Tri(vm,"anti.coagNormal","Coagulation is normal after interruption?")
        if(r?.family=="VKA"){
            Field(vm,"anti.inr","Measured INR",min=0.5,max=15.0)
            Tri(vm,"anti.labsCurrent","INR result reviewed as current for the planned event?")
            Tri(vm,"anti.inrNormal","INR normalized to this laboratory's normal range?")
        }
    }
    Section("Needle and catheter events","These times are optional for the initial withholding calculation"){
        TimeField(vm,"anti.insertion","Actual / proposed spinal or epidural insertion")
        Tri(vm,"anti.catheter","Epidural catheter present / planned?")
        if(c.yes("anti.catheter")){
            Tri(vm,"anti.exposed","Blood thinner given on or after catheter insertion?")
            TimeField(vm,"anti.removal","Actual / proposed catheter removal")
        }
    }
    Section("Restart after surgery","Complete to calculate the restart time",expanded=false){
        TimeField(vm,"anti.surgeryEnd","Actual / proposed end of surgery")
        Choice(vm,"anti.surgicalRisk","Surgical bleeding risk",listOf("Low / moderate","High"))
        Tri(vm,"anti.traumatic","Traumatic / bloody neuraxial puncture?")
        Tri(vm,"anti.hemostasis","Adequate hemostasis confirmed for restarting?")
        Tri(vm,"anti.thrombosis","Interruption / thrombosis plan agreed with treating team?")
        if(r?.family=="P2Y12")Tri(vm,"anti.loading","Loading dose planned at restart?")
        TimeField(vm,"anti.restart","Proposed restart (optional comparison)")
        TimeField(vm,"anti.surgical","Additional team restriction: not before (optional)")
    }
}
@Composable fun RegimenPicker(vm:PlannerVM){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("Which regimen was used?",fontWeight=FontWeight.SemiBold)
        Anticoagulation.regimens.filter{it.drug==vm.c.v("anti.drug")}.forEach{r->
            FilterChip(selected=vm.c.v("anti.regimen")==r.id,onClick={vm.set("anti.regimen",r.id)},
                label={Text(r.label)},modifier=Modifier.fillMaxWidth().testTag("regimen_"+r.id))
        }
    }
}
@Composable fun ProcedureInputs(vm:PlannerVM){
    Field(vm,"age","Age (adult years)",min=18.0,max=120.0)
    Choice(vm,"procedure","Operation",Content.procedures)
}
@Composable fun AirwayInputs(vm:PlannerVM){
    Section("Patient and operation"){
        ProcedureInputs(vm)
        Field(vm,"height","Height (cm)",min=100.0,max=230.0);Field(vm,"weight","Weight (kg)",min=25.0,max=400.0)
        Choice(vm,"urgency","Urgency",listOf("Elective","Time-sensitive","Urgent","Emergency"))
        Choice(vm,"position","Position",listOf("Supine","Prone","Lateral","Lithotomy","Sitting","Other"))
        Field(vm,"duration","Expected duration (minutes)",min=1.0,max=1440.0)
    }
    Section("Airway examination"){
        Field(vm,"airway.opening","Mouth opening (cm)",min=0.1,max=10.0)
        Choice(vm,"airway.mallampati","Mallampati",listOf("I","II","III","IV"))
        Field(vm,"airway.thyromental","Thyromental distance (cm)",min=1.0,max=15.0)
        Choice(vm,"airway.neck","Neck movement",listOf("Normal","Restricted","Immobilized"))
        Choice(vm,"airway.jaw","Mandibular protrusion",listOf("Normal","Limited"))
        Choice(vm,"airway.bite","Upper-lip bite test",listOf("I","II","III"))
        Field(vm,"airway.dentition","Dentition / loose teeth / dentures")
        Tri(vm,"airway.previous","Previous difficult airway")
        Tri(vm,"airway.ventilation","Expected difficult mask / supraglottic ventilation")
        Tri(vm,"airway.aspiration","Increased aspiration risk")
        Tri(vm,"airway.apnea","Expected intolerance of brief apnea")
        Tri(vm,"airway.rescue","Expected difficult emergency front-of-neck access")
    }
    Section("Surgical airway requirements"){
        Tri(vm,"airway.shared","Shared airway / surgeon needs airway access")
        Tri(vm,"airway.lungIsolation","Lung isolation required")
        Tri(vm,"airway.highPressure","Expected high ventilation pressures / poor lung compliance")
        Tri(vm,"osa","Known OSA")
    }
}
@Composable fun ExtubationInputs(vm:PlannerVM){
    Section("Operation and respiratory risks"){
        ProcedureInputs(vm);Field(vm,"height","Height (cm)",min=100.0,max=230.0);Field(vm,"weight","Weight (kg)",min=25.0,max=400.0)
        Tri(vm,"osa","Known OSA")
        Tri(vm,"ext.difficult","Difficult intubation / expected difficult reintubation")
        Tri(vm,"ext.edema","Airway edema, trauma or bleeding concern")
        Tri(vm,"ext.support","Ongoing need for ventilatory support")
    }
    Section("Readiness now"){
        Tri(vm,"ext.awake","Awake and following commands")
        Tri(vm,"ext.ventilation","Adequate ventilation and oxygenation for this patient")
        Tri(vm,"ext.protection","Effective cough and secretion control")
        Tri(vm,"ext.stable","Hemodynamically stable")
        Tri(vm,"ext.nmb","Neuromuscular blocker used")
        if(vm.c.yes("ext.nmb"))Field(vm,"ext.tof","Quantitative TOF ratio · adductor pollicis",min=0.0,max=1.5)
    }
}
@Composable fun AnalgesiaInputs(vm:PlannerVM){
    Section("Surgery and baseline analgesia"){
        ProcedureInputs(vm)
        Choice(vm,"pain.opioid","Opioid / antagonist exposure",DecisionSupport.opioidOptions)
        if(vm.c.v("pain.opioid") !in listOf("","None")){
            Field(vm,"pain.regimen","Drug, dose, route, frequency and duration",true)
            Field(vm,"pain.last","Last administration and recent actual use",true)
        }
    }
    Section("Modify the pain plan"){
        Tri(vm,"pain.allergies","Allergies and analgesic contraindications reviewed")
        Tri(vm,"pain.paracetamolContra","Paracetamol / acetaminophen contraindication")
        Tri(vm,"pain.nsaidContra","NSAID / COX-2 contraindication (renal, GI, bleeding, cardiovascular or allergy)")
        Tri(vm,"renal","Renal disease");Tri(vm,"liver","Liver disease");Tri(vm,"osa","Known OSA")
        Info("Drug doses remain individualized. These inputs choose a technique and medication strategy; they do not convert opioid doses or estimate a safe local-anesthetic dose.")
    }
}
