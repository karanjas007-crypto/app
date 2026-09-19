package com.perioperative.planner

data class Decision(val title:String,val recommendation:String,val reasons:List<String>,
    val actions:List<String>,val sources:List<String>,val missing:List<String> = emptyList())

/** Transparent clinical synthesis; not a validated prediction score or an LLM. */
object DecisionSupport {
    private val airwayBools=linkedMapOf("airway.previous" to "previous difficulty","airway.aspiration" to "aspiration risk",
        "airway.ventilation" to "mask / SGA ventilation","airway.apnea" to "apnea tolerance","airway.rescue" to "emergency front-of-neck access")
    fun airway(c:Case):Decision {
        val risks=mutableListOf<String>();val missing=mutableListOf<String>();val actions=mutableListOf<String>()
        val opening=c.num("airway.opening",0.1,10.0);val thyro=c.num("airway.thyromental",1.0,15.0)
        if(opening==null)missing+="Mouth opening" else if(opening<3)risks+="Mouth opening ${c.v("airway.opening")} cm: restricted oral access"
        if(c.v("airway.mallampati").isBlank())missing+="Mallampati" else if(c.v("airway.mallampati") in listOf("III","IV"))risks+="Mallampati ${c.v("airway.mallampati") }"
        if(c.v("airway.neck").isBlank())missing+="Neck movement" else if(c.v("airway.neck")!="Normal")risks+="Neck movement ${c.v("airway.neck").lowercase()}"
        if(thyro==null)missing+="Thyromental distance" else if(thyro<6)risks+="Short thyromental distance"
        if(c.v("airway.jaw").isBlank())missing+="Jaw protrusion" else if(c.v("airway.jaw")=="Limited")risks+="Limited jaw protrusion"
        if(c.v("airway.bite").isBlank())missing+="Upper-lip bite test" else if(c.v("airway.bite")=="III")risks+="Upper-lip bite class III"
        airwayBools.forEach{(k,label)->if(c.v(k) !in listOf("Yes","No"))missing+=label else if(c.yes(k))risks+=label}
        if(c.v("airway.dentition").isBlank())missing+="Dentition"
        if(c.v("procedure").isBlank())missing+="Operation"
        if(c.v("position").isBlank())missing+="Position"
        if(c.num("age",18.0,120.0)==null)missing+="Adult age"
        if(Engine.bmi(c)==null)missing+="Height and weight"
        listOf("airway.highPressure" to "Ventilation pressures / compliance","airway.lungIsolation" to "Lung isolation", "airway.shared" to "Shared airway").forEach{(k,label)->if(c.v(k) !in listOf("Yes","No"))missing+=label}
        if(c.v("urgency").isBlank())missing+="Urgency"
        if(c.num("duration",1.0,1440.0)==null)missing+="Duration"
        val anatomical=(opening!=null && opening<3) || (thyro!=null && thyro<6) || c.yes("airway.previous") ||
            c.v("airway.mallampati") in listOf("III","IV") || c.v("airway.neck") in listOf("Restricted","Immobilized") ||
            c.v("airway.jaw")=="Limited" || c.v("airway.bite")=="III" || c.v("procedure")=="Head / neck / airway surgery"
        val awakeTriggers=airwayBools.keys.filter{it!="airway.previous" && c.yes(it)}
        val obese=(Engine.bmi(c)?:0.0)>=30
        val tubeRequired=c.yes("airway.aspiration") || c.yes("airway.highPressure") || c.yes("airway.lungIsolation") ||
            c.yes("airway.shared") || c.v("position")=="Prone" || c.v("procedure") in listOf("CABG","Liver transplantation","Whipple","Trauma","Head / neck / airway surgery")
        val sgaReviewed=listOf("airway.highPressure","airway.lungIsolation","airway.shared").all{c.no(it)}
        val shortOperation=c.v("procedure") in listOf("Short peripheral / superficial surgery","TURP") &&
            c.num("duration",1.0,120.0)!=null && c.v("urgency")=="Elective" && c.v("position") in listOf("Supine","Lithotomy")
        val recommendation=when{
            c.num("age",18.0,120.0)==null -> "Adult airway assessment incomplete"
            opening!=null && opening<=2 -> "Consider awake flexible-scope intubation; severe oral-access restriction"
            anatomical && awakeTriggers.isNotEmpty() -> "Favor an awake intubation strategy, with skilled assistance"
            anatomical -> "Anticipated difficult intubation: consider video laryngoscopy; finish the awake-versus-asleep assessment"
            tubeRequired -> "If general anesthesia is used, favor a cuffed tracheal tube with a planned rescue strategy"
            missing.isNotEmpty() -> "Complete the assessment before selecting an airway device"
            shortOperation && sgaReviewed && !obese -> "A second-generation supraglottic airway may be suitable for this selected case"
            else -> "Tracheal intubation is a reasonable general-anesthesia option; choose the device after bedside assessment"
        }
        if(obese){
            risks+="BMI ${decValue(Engine.bmi(c))} kg/m²"
            actions+="Use ramped, head-up preoxygenation (at least 30° where feasible); consider high-flow nasal oxygen. Video laryngoscopy is a first-line option in obesity."
        }else actions+="Optimize positioning and preoxygenate; maintain oxygen delivery during airway management."
        if(anatomical)actions+="Prepare the primary device, trained assistance and a rescue oxygenation / emergency front-of-neck plan. Limit repeated attempts and reassess oxygenation."
        if(opening!=null && opening<=2)actions+="A flexible scope may fit restricted access better than an oral blade. Choose the route after checking anatomy, bleeding risk and surgical access; mouth opening alone cannot establish feasibility."
        if(c.yes("airway.aspiration"))actions+="Use a protected-airway strategy. Decide between awake intubation and rapid-sequence induction using the complete difficulty and apnea-risk assessment."
        if(c.yes("airway.lungIsolation"))actions+="Lung isolation needs a specialist tube / bronchial-blocker plan and bronchoscopic confirmation."
        if(c.yes("airway.shared") || c.v("procedure")=="Head / neck / airway surgery")actions+="Agree airway access and rescue with the surgeon before induction; procedure type alone does not determine the device."
        if(c.v("procedure")=="Cesarean delivery")actions+="For general anesthesia, use the obstetric aspiration and difficult-airway pathway; neuraxial feasibility is a separate decision."
        actions+="Confirm tracheal placement with sustained exhaled CO₂. A predicted easy airway still needs rescue equipment."
        return Decision("Airway recommendation",recommendation,risks.ifEmpty{listOf("No adverse finding entered; unanswered findings are listed separately.")},actions,
            listOf("asa","dasAti")+(if(obese)listOf("soba")else emptyList()),missing)
    }
    fun extubation(c:Case):Decision {
        val missing=mutableListOf<String>();val failed=mutableListOf<String>();val risks=mutableListOf<String>()
        val readiness=linkedMapOf("ext.awake" to "awake and following commands","ext.ventilation" to "adequate ventilation and oxygenation",
            "ext.protection" to "effective cough / secretion control","ext.stable" to "hemodynamic stability")
        readiness.forEach{(k,s)->when(c.v(k)){"No"->failed+=s;"Yes"->Unit;else->missing+=s}}
        if(c.num("age",18.0,120.0)==null)missing+="Adult age"
        if(Engine.bmi(c)==null)missing+="Height and weight"
        if(c.v("osa") !in listOf("Yes","No"))missing+="OSA status"
        when(c.v("ext.nmb")){
            "Yes"->{val tof=c.num("ext.tof",0.0,1.5);if(tof==null)missing+="Quantitative TOF ratio at adductor pollicis" else if(tof<0.9)failed+="Quantitative TOF ${c.v("ext.tof")} is below 0.9"}
            "No"->Unit
            else->missing+="Neuromuscular blocker used?"
        }
        listOf("ext.difficult" to "Difficult intubation / expected difficult reintubation","ext.edema" to "Airway edema, trauma or bleeding",
            "ext.support" to "Ongoing need for ventilatory support").forEach{(k,label)->
            if(c.yes(k))risks+=label else if(!c.no(k))missing+=label
        }
        if(c.yes("ext.support"))failed+="Ongoing need for ventilatory support"
        if(c.yes("osa") || Engine.osa(c)=="High screening risk")risks+="OSA / high screening risk"
        if((Engine.bmi(c)?:0.0)>=30)risks+="Obesity"
        if(c.v("procedure")=="Head / neck / airway surgery")risks+="Airway surgery"
        if(c.v("procedure").isBlank())missing+="Operation"
        val result=when{
            failed.isNotEmpty()->"Defer extubation; correct the unmet readiness criteria"
            missing.isNotEmpty()->"Extubation readiness is not established"
            risks.isNotEmpty()->"Plan awake extubation with a defined reintubation strategy"
            else->"Awake extubation is reasonable after the bedside readiness check"
        }
        val actions=mutableListOf<String>()
        if(failed.isNotEmpty())actions+="Continue appropriate airway / ventilatory support and reassess: "+failed.joinToString(", ")+"."
        actions+="Ensure skilled help, oxygen, suction and reintubation equipment; choose a suitable location and postoperative monitoring."
        if(c.yes("ext.edema"))actions+="Assess airway patency and the need for delayed extubation. A cuff-leak result alone cannot exclude obstruction."
        if(c.yes("ext.difficult") || c.v("procedure")=="Head / neck / airway surgery")actions+="Consider staged extubation with an airway-exchange catheter only with appropriate expertise, equipment and a rescue plan."
        if("Obesity" in risks || c.yes("osa"))actions+="Extubate head-up once fully awake; consider CPAP/NIV or high-flow oxygen according to respiratory status and the established treatment plan."
        actions+="If a blocker was used, verify quantitative neuromuscular recovery (TOF ≥0.9 at adductor pollicis)."
        return Decision("Extubation recommendation",result,failed+risks,actions,listOf("asa","nmb")+(if("Obesity" in risks)listOf("soba")else emptyList()),missing)
    }
    val opioidOptions=listOf("None","Intermittent use","Daily full agonist","Buprenorphine","Methadone","Naltrexone")
    fun analgesia(c:Case):Decision {
        if(c.num("age",18.0,120.0)==null)return Decision("Analgesia recommendation","Adult age required before generating a pain plan",emptyList(),listOf("Enter an adult age; pediatric analgesia is outside this preview."),listOf("local"),listOf("Adult age"))
        val missing=mutableListOf<String>();val actions=mutableListOf<String>();val reasons=mutableListOf<String>();val refs=mutableListOf<String>()
        val p=c.v("procedure");val exposure=c.v("pain.opioid")
        if(p.isBlank())missing+="Operation"
        if(c.num("age",18.0,120.0)==null)missing+="Adult age"
        if(exposure !in opioidOptions)missing+="Opioid / antagonist exposure"
        if(exposure in opioidOptions && exposure!="None"){
            if(c.v("pain.regimen").isBlank())missing+="Drug, actual dose, route, frequency and duration"
            if(c.v("pain.last").isBlank())missing+="Last opioid / antagonist administration"
        }
        if(!c.yes("pain.allergies"))missing+="Allergies and analgesic contraindications reviewed"
        listOf("renal" to "Renal disease","liver" to "Liver disease","osa" to "OSA status").forEach{(k,label)->if(c.v(k) !in listOf("Yes","No"))missing+=label}
        val base=when(p){
            "Knee replacement"->{refs+="prospectKnee";"Single-shot adductor-canal block plus periarticular local infiltration, with multimodal systemic analgesia"}
            "Hip replacement"->{refs+="prospectHip";"Consider suprainguinal fascia-iliaca or preoperative PENG block with multimodal analgesia; local infiltration is an alternative when regional block is unavailable"}
            "Cesarean delivery"->{refs+="prospectCS";"For elective neuraxial cesarean delivery, consider a long-acting neuraxial opioid plus nonopioid analgesia"}
            "Whipple"->{refs+="erasPD";"For open Whipple surgery, consider thoracic epidural analgesia if coagulation and hemodynamics permit; use a multimodal alternative when unsuitable"}
            "CABG"->{refs+="prospectSternotomy";"For sternotomy, consider parasternal block / wound infiltration with multimodal analgesia and opioid rescue"}
            "TURP"->"Use nonopioid analgesia when eligible; assess catheter-related discomfort and bladder spasm before escalating opioids"
            "Trauma"->"Treat resuscitation priorities alongside titrated analgesia; select injury-specific regional options after bleeding and neurological review"
            "Liver transplantation"->"Use the transplant pain pathway, with individualized drug selection for graft, renal and coagulation status"
            "Head / neck / airway surgery"->"Use operation-specific local / regional and nonopioid options, with particular attention to airway and bleeding risk"
            "Short peripheral / superficial surgery"->"Use local infiltration or a site-specific peripheral block with nonopioid analgesia when eligible"
            else->"Select an incision- and organ-specific multimodal plan; no validated procedure pack is assigned"
        }
        reasons+="Operation: "+p.ifBlank{"unknown"};reasons+="Baseline exposure: "+exposure.ifBlank{"unknown"}
        if(c.v("pain.regimen").isNotBlank())reasons+="Reported regimen: "+c.v("pain.regimen")
        fun medicine(k:String,name:String,blocked:Boolean=false){
            when {
                c.yes(k)||blocked->actions+="Omit routine $name; a contraindication or organ-function concern is recorded."
                c.no(k)&&c.yes("pain.allergies")->actions+="Use $name as a nonopioid component if clinically appropriate; verify dose, cumulative exposure and formulary restrictions."
                else->{missing+="$name eligibility";actions+="Assess eligibility for $name before adding it."}
            }
        }
        if(c.yes("liver"))actions+="Review hepatic function, cumulative paracetamol exposure and an appropriate dose ceiling before selecting paracetamol / acetaminophen."
        else if(!c.no("liver"))actions+="Clarify hepatic status before selecting paracetamol / acetaminophen."
        else medicine("pain.paracetamolContra","paracetamol / acetaminophen")
        if(c.v("renal") !in listOf("Yes","No") && p!="CABG")actions+="Clarify renal status before selecting an NSAID / COX-2 inhibitor."
        else medicine("pain.nsaidContra","NSAID / COX-2 inhibitor",c.yes("renal") || p=="CABG")
        if(p=="Knee replacement")actions+="Consider one intraoperative IV dexamethasone dose. Avoid routine gabapentinoids in the standard knee pathway; reserve opioids for breakthrough pain."
        if(p=="Hip replacement")actions+="Consider a single intraoperative IV dexamethasone dose and reserve opioids for rescue. Assess motor weakness and mobilisation needs; routine quadratus-lumborum or lumbar erector-spinae block is not supported by the 2026 pathway."
        if(p=="Cesarean delivery")actions+="If a long-acting neuraxial opioid is omitted, consider wound infiltration or a fascial-plane block. Provide the required neuraxial-opioid respiratory monitoring."
        if(p=="Whipple")actions+="Consider wound catheters as an epidural alternative within an ERAS pathway. Abdominal-wall blocks do not reliably replace visceral analgesia; agree rescue therapy and monitor epidural-related hypotension."
        when(exposure){
            "None"->actions+="For breakthrough pain, titrate a short-acting opioid to effect and reassess sedation and ventilation."
            "Intermittent use"->actions+="Do not infer opioid tolerance from occasional use. Verify recent exposure before titrating rescue analgesia."
            "Daily full agonist"->{refs+="opioidConsensus";actions+="Verify and coordinate continuation of established baseline therapy to avoid withdrawal; provide additional analgesia for the operation. Consider acute-pain review and a monitored PCA when appropriate."}
            "Buprenorphine"->{refs+="buprenorphine";actions+="Do not routinely stop buprenorphine used for OUD. Coordinate with the prescriber, use regional / multimodal analgesia, and obtain specialist support for additional opioid therapy or dose division."}
            "Methadone"->{refs+="methadone";actions+="Verify the maintenance dose and recent actual use with the treating service; coordinate continuation. After missed doses or impaired absorption, reassess tolerance with the service. Maintenance therapy does not cover surgical pain; review QT interval, interactions and additional analgesia."}
            "Naltrexone"->{refs+="naltrexone";actions+="Opioid antagonism can alter response. Obtain a prescriber / acute-pain plan based on formulation and last dose; prioritize regional and nonopioid options. If opioids are needed during depot blockade, use specialist monitoring with resuscitation capability. Do not automatically escalate opioids or calculate a conversion."}
        }
        if(c.yes("osa") || Engine.osa(c)=="High screening risk"){
            refs+="sasm";actions+="Prioritize opioid-sparing techniques, avoid unnecessary sedative combinations, and plan respiratory monitoring / prescribed PAP for OSA."
        }
        if(c.yes("renal"))actions+="Review renal clearance of every analgesic and active metabolite before prescribing."
        if(c.v("anti.drug").isNotBlank()){
            refs+="asra";actions+="Before neuraxial or deep plexus / deep peripheral techniques, use the anticoagulant module. Other block sites need a site-specific bleeding assessment."
        }
        actions+="Reassess pain with movement, function, adverse effects and rescue use; agree a discharge taper / return to baseline with the prescriber."
        if(refs.isEmpty())refs+="local"
        return Decision("Analgesia recommendation",base,reasons,actions,refs.distinct(),missing)
    }
    private fun decValue(n:Double?)=n?.let{String.format(java.util.Locale.US,"%.1f",it)}?:"unknown"
}
