package com.perioperative.planner

import java.time.Instant
import java.time.OffsetDateTime

/** Numeric facts: ASRA 5th ed (2025), table 1 and drug-specific recommendations.
 * Deliberately separate needle placement, removal and postoperative resumption.
 * Values at the upper end of a guideline range are conservative app choices.
 */
data class Regimen(val id:String, val drug:String, val label:String, val hold:Long,
    val afterNeedle:Long, val afterRemoval:Long, val family:String,
    val maintenance:Boolean=false, val note:String="")

object Anticoagulation {
    val regimens=listOf(
        Regimen("apix_low","Apixaban","2.5 mg twice daily · VTE prevention / extended VTE prevention",36,6,6,"DOAC"),
        Regimen("apix_high","Apixaban","Atrial fibrillation (including 2.5 mg twice daily) / VTE treatment",72,24,24,"DOAC"),
        Regimen("riva_low","Rivaroxaban","10 mg daily · VTE prevention / extended prevention",24,6,6,"DOAC"),
        Regimen("riva_high","Rivaroxaban","15 or 20 mg daily for AF / 15 mg twice daily or 20 mg daily for VTE",72,24,24,"DOAC"),
        Regimen("dabi_low","Dabigatran","Hip-replacement prophylaxis · 110 mg first day, then 220 mg daily",48,6,6,"DOAC"),
        Regimen("dabi_high","Dabigatran","AF / VTE treatment or secondary prevention · including reduced doses",72,24,24,"DOAC"),
        Regimen("edox_high","Edoxaban","AF / VTE treatment · 60 mg or dose-reduced 30 mg daily",72,24,24,"DOAC"),
        Regimen("enox_daily","Enoxaparin","40 mg subcutaneous once daily",12,12,4,"LMWH",true),
        Regimen("enox_bid","Enoxaparin","30 mg subcutaneous every 12 hours",12,12,4,"LMWH",false,"First postoperative dose: following day as well as ≥12 h after insertion."),
        Regimen("enox_high","Enoxaparin","1 mg/kg every 12 hours or 1.5 mg/kg daily",24,24,4,"LMWH",false,"Time-only calculation is limited to CrCl ≥50 mL/min. Review residual activity in elderly or morbidly obese patients; 24 h may not ensure drug clearance."),
        Regimen("ufh_low","UFH","5,000 units subcutaneous twice or three times daily",6,0,0,"UFH",true,"ASRA range 4–6 h; calculator uses 6 h."),
        Regimen("ufh_mid","UFH","7,500–10,000 units subcutaneous twice daily; ≤20,000 units/day",12,0,0,"UFH",false,"Normal coagulation required. Postoperative dosing needs an individual plan."),
        Regimen("ufh_high","UFH",">10,000 units per subcutaneous dose or >20,000 units/day",24,0,0,"UFH",false,"Normal coagulation required. Postoperative dosing needs an individual plan."),
        Regimen("ufh_iv","UFH","Intravenous infusion · enter time infusion stopped",6,1,1,"UFH",false,"ASRA range 4–6 h; calculator uses 6 h. Normal coagulation required."),
        Regimen("warfarin","Warfarin","Established oral warfarin therapy",120,0,0,"VKA",true,"Insertion also needs INR within the local laboratory normal range. Removal needs INR <1.5."),
        Regimen("clopidogrel","Clopidogrel","Oral clopidogrel",168,0,0,"P2Y12",false,"ASRA range 5–7 days; calculator uses 7 days. Shorter holds need individual review."),
        Regimen("prasugrel","Prasugrel","Oral prasugrel",240,0,0,"P2Y12",false,"ASRA range 7–10 days; calculator uses 10 days."),
        Regimen("ticagrelor","Ticagrelor","Oral ticagrelor",120,0,0,"P2Y12"),
        Regimen("aspirin","Aspirin","Aspirin alone",0,0,0,"NSAID",true,"No ASRA neuraxial withholding interval for aspirin alone.")
    )
    val drugs=regimens.map{it.drug}.distinct()+"Other / regimen not listed"
    fun selected(c:Case)=regimens.firstOrNull{it.id==c.v("anti.regimen") && it.drug==c.v("anti.drug")}
    private fun plus(t:Instant,h:Long)=t.plusSeconds(h*3600)
    private fun assessed(target:Instant?,lower:Instant?)=when {
        lower==null -> "Cannot calculate yet"
        target==null -> "Earliest guideline-based time"
        target<lower -> "Proposed time is too early"
        else -> "Minimum timing criterion met"
    }
    fun evaluate(c:Case,now:Instant=Instant.now()):Timeline {
        val missing=mutableListOf<String>()
        val r=selected(c)
        val titles=listOf("Spinal anesthesia","Epidural insertion","Epidural catheter removal","Restart medication")
        if(r==null) return Timeline(titles.map{Timing(it,"Select medication and regimen")},
            listOf("Choose a listed regimen. Other doses, combinations, fondaparinux and thrombolytics need source-specific review; no interval is guessed."),"asra")
        fun need(ok:Boolean,s:String){if(!ok)missing+=s}
        val last=Engine.time(c.v("anti.last"));val insert=Engine.time(c.v("anti.insertion"))
        val removal=Engine.time(c.v("anti.removal"));val restart=Engine.time(c.v("anti.restart"))
        val end=Engine.time(c.v("anti.surgeryEnd"));val catheter=c.yes("anti.catheter")
        val crcl=c.num("anti.crcl",1.0,200.0)
        need(c.num("age",18.0,120.0)!=null,"Adult age required")
        need(c.no("anti.other"),"Review additional antithrombotics / interacting drugs; combination rules are not automated")
        need(c.no("anti.bleeding"),"Exclude active bleeding, thrombocytopenia and other hemostatic concerns")
        if(r.hold>0)need(last!=null && last<=now,"Valid last administered dose (not in the future)")
        if(r.family in listOf("DOAC","LMWH")){
            need(crcl!=null,"Creatinine clearance in mL/min (not indexed eGFR)")
            need(c.no("anti.aki"),"Exclude acute / unstable renal impairment")
            val floor=when(r.id){"riva_low"->15.0;"enox_high"->50.0;else->30.0}
            need(crcl!=null && crcl>=floor,"Renal function outside implemented time-only scope; specialist / assay review needed")
        }
        if(r.family in listOf("UFH","LMWH")){
            need(c.v("anti.over4days") in listOf("Yes","No"),"Has heparin/LMWH been used for more than 4 days?")
            if(c.yes("anti.over4days"))need(c.yes("anti.platelets"),"Current platelet count / HIT assessment required after >4 days of heparin/LMWH")
        }
        if(r.family=="UFH" && r.id!="ufh_low")need(c.yes("anti.coagNormal"),"Current normal coagulation after UFH interruption")
        val commonReady=missing.isEmpty()
        var hold=r.hold
        if(r.id=="riva_low" && crcl!=null && crcl<30)hold=30
        if(r.id=="dabi_high" && crcl!=null && crcl<50)hold=120
        var lower=if(commonReady)if(hold==0L)now else last?.let{plus(it,hold)} else null
        if(r.family=="VKA" && (!c.yes("anti.inrNormal") || c.num("anti.inr",0.5,1.49)==null || !c.yes("anti.labsCurrent"))){
            lower=null;missing+="Insertion: measured INR, current for the event, normalized to local laboratory range"
        }
        val exposed=catheter && c.yes("anti.exposed")
        val insertionDetail=if(r.hold==0L)r.note else "Last dose + $hold h. ${r.note}".trim()
        val insertState=when {exposed->"Historical insertion: assess separately";commonReady && hold==0L->"No drug-specific withholding interval";else->assessed(insert,lower)}
        val rows=mutableListOf(
            Timing(titles[0],if(exposed)"Existing catheter scenario" else insertState,if(exposed || hold==0L)null else lower,insertionDetail),
            Timing(titles[1],insertState,if(exposed || hold==0L)null else lower,insertionDetail))
        // Removal is a separate decision: a historical insertion problem must not
        // be silently turned into a recommendation to leave a catheter forever.
        val removalIssues=mutableListOf<String>()
        var removeLower:Instant?=null
        var removalValid=false
        var removalDetail=""
        if(catheter){
            if(c.v("anti.exposed") !in listOf("Yes","No"))removalIssues+="Catheter exposure history"
            if(insert==null)removalIssues+="Insertion date/time"
            if(removal!=null && insert!=null && removal<insert)removalIssues+="Removal precedes insertion"
            if(exposed && (last==null || insert==null || last<insert))removalIssues+="Dose-with-catheter history conflicts with event times"
            if(exposed && last!=null && removal!=null && last>removal)removalIssues+="Last dose follows the entered catheter removal"
            if(!exposed && last!=null && insert!=null && last>=insert && r.hold>0)removalIssues+="A dose on/after insertion contradicts continuous withholding"
            when {
                r.family=="VKA" -> {
                    val inr=c.num("anti.inr",0.5,15.0)
                    if(inr==null || inr>=1.5 || !c.yes("anti.labsCurrent"))removalIssues+="Removal: current INR <1.5 required; other INR values need individual management"
                    removalDetail="Use current INR, not a fixed last-dose interval. Continue neurological assessment for ≥48 h after removal."
                }
                exposed && r.family=="DOAC" -> {removeLower=last?.let{plus(it,hold)};removalDetail="Unanticipated DOAC exposure: last dose + $hold h; specialist assay alternatives are not automated."}
                exposed && r.id=="enox_daily" -> {removeLower=last?.let{plus(it,12)};removalDetail="Last once-daily low-dose LMWH + 12 h."}
                exposed && r.id=="ufh_low" -> {removeLower=last?.let{plus(it,6)};removalDetail="Last low-dose SC UFH + 6 h (upper end of 4–6 h range)."}
                exposed && r.id!="aspirin" -> {
                    removalIssues+="This exposure needs an individual catheter-removal plan"
                    removalDetail=if(r.id=="clopidogrel")"ASRA permits a limited 1–2-day catheter window without a loading dose; duration and full dosing history require review."
                        else "Do not apply the insertion interval automatically to this catheter exposure. Review the drug-specific source."
                }
                else -> removalDetail="No additional fixed drug delay after continuous withholding; confirm current hemostasis and removal plan."
            }
            removalValid=commonReady && removalIssues.isEmpty() && c.v("anti.exposed") in listOf("Yes","No") && insert!=null &&
                removal!=null && removal>=insert && (removeLower==null || removal>=removeLower)
            if(!commonReady || removalIssues.isNotEmpty())removeLower=null
            rows+=Timing(titles[2],when{
                !commonReady || removalIssues.isNotEmpty()->"Review required"
                removeLower!=null->assessed(removal,removeLower)
                r.family=="VKA"->"INR criterion met; confirm removal plan"
                else->"No additional fixed drug delay"
            },removeLower,removalDetail)
        }else rows+=Timing(titles[2],if(c.no("anti.catheter"))"Not applicable"else"Catheter status needed")
        missing+=removalIssues
        val restartIssues=mutableListOf<String>()
        if(c.v("anti.catheter") !in listOf("Yes","No"))restartIssues+="Catheter present / planned?"
        if(insert==null)restartIssues+="Needle placement date/time"
        if(catheter && !removalValid)restartIssues+="Valid catheter removal time and assessment"
        if(!catheter && (lower==null || insert==null || insert<lower && r.hold>0))restartIssues+="Needle placement does not meet the calculated interval"
        if(catheter && !exposed && r.family!="VKA" && lower!=null && insert!=null && r.hold>0 && insert<lower)
            restartIssues+="Original catheter insertion precedes the calculated interval; review before resumption"
        if(!c.yes("anti.hemostasis"))restartIssues+="Adequate hemostasis for resumption"
        if(!c.yes("anti.thrombosis"))restartIssues+="Interruption / thrombosis plan agreed with treating team"
        if(c.v("anti.traumatic") !in listOf("Yes","No"))restartIssues+="Traumatic puncture status"
        if(end==null || insert!=null && end<insert)restartIssues+="Surgery end must follow needle placement"
        if(r.family=="DOAC" || r.id=="enox_high"){
            if(c.v("anti.surgicalRisk") !in listOf("Low / moderate","High"))restartIssues+="Surgical bleeding risk"
        }
        if(r.family=="P2Y12" && c.v("anti.loading") !in listOf("Yes","No"))restartIssues+="Loading dose planned?"
        if(r.id in listOf("ufh_mid","ufh_high"))restartIssues+="Postoperative high-dose SC UFH requires an individual plan"
        if(c.yes("anti.traumatic") && r.drug !in listOf("Apixaban","Rivaroxaban","Enoxaparin"))
            restartIssues+="Traumatic puncture: individualized resumption required for this drug"
        var resume:Instant?=null
        val formula=mutableListOf<String>()
        if(commonReady && restartIssues.isEmpty() && insert!=null && end!=null){
            val candidates=mutableListOf(end,plus(insert,r.afterNeedle))
            formula+="needle + ${r.afterNeedle} h"
            if(catheter && removal!=null){candidates+=plus(removal,r.afterRemoval);formula+="removal + ${r.afterRemoval} h"}
            if(r.family=="DOAC" || r.id=="enox_high"){
                val surgeryHours=if(c.v("anti.surgicalRisk")=="High")72L else 24L
                candidates+=plus(end,surgeryHours);formula+="surgery end + $surgeryHours h"
            }
            if(r.id=="enox_bid"){
                val z=OffsetDateTime.parse(c.v("anti.insertion").trim())
                candidates+=z.toLocalDate().plusDays(1).atStartOfDay().atOffset(z.offset).toInstant()
                formula+="following calendar day (insertion UTC offset)"
            }
            if(r.id=="enox_daily" && exposed && last!=null){candidates+=plus(last,24);formula+="previous postoperative dose + 24 h"}
            if(r.family=="P2Y12" && c.yes("anti.loading")){
                candidates+=plus(if(catheter)removal!! else insert,6);formula+="loading-dose interval 6 h"
            }
            if(c.yes("anti.traumatic")){
                when(r.drug){
                    "Apixaban"->{candidates+=plus(insert,48);formula+="traumatic needle + 48 h"}
                    "Rivaroxaban"->{candidates+=plus(insert,24);formula+="traumatic needle + 24 h"}
                    "Enoxaparin"->{candidates+=plus(end,24);formula+="traumatic puncture: surgery end + 24 h"}
                }
            }
            // Optional extra restriction can only lengthen the recommendation.
            if(c.v("anti.surgical").isNotBlank()){
                val extra=Engine.time(c.v("anti.surgical"))
                if(extra==null)restartIssues+="Invalid additional restart restriction" else {candidates+=extra;formula+="additional team restriction"}
            }
            if(restartIssues.isEmpty())resume=candidates.maxOrNull()
        }
        rows+=Timing(titles[3],assessed(restart,resume),resume,
            if(formula.isEmpty())"Complete the event and hemostasis inputs to calculate resumption."
            else "Use the latest of: "+formula.joinToString("; ")+". "+
                (if((r.family=="DOAC" || r.id=="enox_high") && c.v("anti.surgicalRisk")=="High")"72 h is the conservative end of the postoperative 48–72 h range. "else"")+ 
                "This calculates resumption of established therapy; it does not prescribe initiation or bridging.")
        missing+=restartIssues.map{"Restart: $it"}
        return Timeline(rows,missing.distinct(),"asra")
    }
}
