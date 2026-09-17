package com.perioperative.planner

object Content {
    val conditions = linkedMapOf("diabetes" to "Diabetes", "osa" to "Known OSA", "pulmonary" to "Pulmonary disease",
        "ph" to "Pulmonary hypertension", "renal" to "Renal disease", "liver" to "Liver disease",
        "mh" to "MH susceptibility", "opioid" to "Chronic opioid use", "frailty" to "Frailty", "anemia" to "Anemia",
        "nutrition" to "Nutrition concerns", "delirium" to "Delirium vulnerability", "ponv" to "PONV risk")
    val procedures = listOf("Knee replacement", "Hip replacement", "CABG", "Liver transplantation", "Whipple", "TURP", "Cesarean delivery", "Trauma", "Head / neck / airway surgery", "Short peripheral / superficial surgery", "Other")
    val commonPrep = listOf("Confirm procedure, side, consent and allergies", "Review airway equipment and assistance",
        "Confirm access, monitoring and blood plan", "Review medicines and antithrombotic timing", "Review investigations",
        "Agree recovery destination and handover")
    fun prep(p: String) = commonPrep + when(p) {
        "Knee replacement", "Hip replacement" -> listOf("Confirm incision, position, tourniquet and duration", "Prepare regional equipment if selected", "Plan motor assessment and assisted mobilization")
        "CABG" -> listOf("Complete cardiac-specific risk assessment", "Agree invasive monitoring and defibrillation readiness",
            "Plan TEE indication, contraindications and assessment stages", "Coordinate bypass/perfusion preparation",
            "Review anticoagulation, reversal and bleeding protocol", "Prepare vasoactive support", "Arrange ICU/ventilation handover")
        "Liver transplantation" -> listOf("Review transplant-specific assessment", "Confirm blood, coagulation support and rapid infusion",
            "Prepare warming, access and serial labs", "Plan dissection, anhepatic and reperfusion phases", "Agree hemodynamic contingencies and ICU handover")
        "Whipple" -> listOf("Review nutrition, jaundice/infection and optimization", "Discuss analgesia options and hemodynamic effects",
            "Agree fluid and access plan", "Plan glucose, nutrition and postoperative recovery")
        "TURP" -> listOf("Confirm monopolar/bipolar technique and irrigation fluid", "Agree regional/general options",
            "Plan absorption, fluid and electrolyte surveillance", "Prepare response to irrigation-related complications")
        "Cesarean delivery" -> listOf("Document maternal/fetal urgency", "Review hemorrhage, aspiration and airway plan",
            "Prepare maternal resuscitation and neonatal coordination", "Confirm block assessment and conversion contingency",
            "Plan postpartum bleeding surveillance and analgesia")
        "Trauma" -> listOf("Prioritize hemorrhage control and resuscitation", "Assess airway and cervical-spine constraints",
            "Prepare blood protocol, warming and rapid access", "Review aspiration and neurological findings", "Agree critical-care destination")
        else -> emptyList()
    }
    fun regional(p: String) = when(p) {
        "Knee replacement" -> "Discuss neuraxial/general options. Consider procedure-specific adductor-canal and periarticular analgesia options; assess motor function, incision and tourniquet coverage."
        "Hip replacement" -> "Discuss neuraxial/general options and incision-specific regional analgesia. Account for approach, motor function, anticoagulation and duration."
        "Whipple" -> "Compare epidural, abdominal-wall and systemic multimodal options. Consider visceral pain, incision, anticoagulation and hemodynamic effects."
        "TURP" -> "Discuss neuraxial/general options; confirm procedure-specific coverage, irrigation technique and expected duration."
        "Cesarean delivery" -> "Base selection on urgency, maternal status, airway and anticoagulation. Document bilateral surgical block assessment and a prompt inadequate-block pathway."
        "Trauma" -> "Consider regional analgesia after reviewing resuscitation, neurological assessment, compartment concerns and coagulation."
        "CABG", "Liver transplantation" -> "Use the specialist team's anesthetic and analgesic pathway, with bleeding, organ function and recovery considerations."
        else -> "Document incision, depth, tourniquet, position and duration; match techniques to patient findings and required coverage."
    }
    fun advice(c: Case): List<Advice> {
        val list = mutableListOf<Advice>()
        fun add(id: String, section: String, title: String, trigger: String, pre: String, intra: String, post: String, source: String, missing: String = "") {
            list += Advice(id, section, title, trigger, pre, intra, post, source, missing)
        }
        if ((c.num("airway.opening", 0.1, 10.0)?.let { it <= 2 } == true) || c.yes("airway.previous") || c.yes("airway.ventilation"))
            add("airway-access","Airway","Develop the difficult-airway strategy","Restricted access / previous difficulty / ventilation concern",
                "Assess awake-intubation feasibility using the complete airway, aspiration, apnea-tolerance and rescue-access findings. Opening alone does not select a device.",
                "Specify oxygenation, primary device, assistance, attempt limits, rescue oxygenation and emergency invasive access.",
                "Document extubation conditions and reintubation strategy.","asa",
                listOf("airway.aspiration","airway.ventilation","airway.apnea","airway.rescue").filter { c.v(it) !in listOf("Yes","No") }.joinToString { it.substringAfter('.') })
        if ((Engine.bmi(c) ?: 0.0) >= 35) add("ramp","Airway","Plan positioning and oxygenation","BMI ≥35 kg/m²",
            "Assess head-elevated/ramped positioning, equipment capacity and preoxygenation.","Adapt position and oxygenation to physiology and procedure access.",
            "Review extubation position and support needs.","asa")
        if (c.yes("airway.aspiration")) add("aspiration","Airway","Review aspiration precautions","Aspiration concern selected",
            "Review fasting, gastrointestinal symptoms, urgency and the full airway assessment.","Document induction, oxygenation and rescue strategy.",
            "Reassess protective reflexes and respiratory status.","asa")
        if (c.yes("osa") || Engine.osa(c) == "High screening risk" || Engine.score(c,Engine.stop).positive >= 5)
            add("osa","Respiratory","Plan OSA precautions","Known OSA / high screening risk",
                "Clarify prescribed PAP, settings, adherence and availability.","Consider opioid-sparing analgesia and sensitivity to respiratory depressants.",
                "Specify monitoring and destination; continue existing prescribed PAP when appropriate. New settings require assessment.","sasm",
                if(c.v("resp.pap").isBlank()) "PAP prescription and use" else "")
        if(c.yes("diabetes")) {
            if((c.num("diabetes.a1c",3.0,25.0) ?: 0.0) > 8.0)
                add("a1c-review","Diabetes","Review glycemic optimization","HbA1c >8%",
                    "Review optimization opportunities alongside urgency and the planned operation. HbA1c alone does not cancel surgery.",
                    "Agree the glucose and monitoring plan.","Coordinate follow-up and treatment review.","ada")
            add("diabetes","Diabetes","Make the glycemic plan","Diabetes selected",
                "Review diabetes type, glucose, recent HbA1c and medication regimen. Elevated HbA1c prompts optimization review; it alone does not determine postponement.",
                "Usual perioperative glucose target 100–180 mg/dL, individualized for the setting. Specify monitoring and hypoglycemia management. IV insulin requires a named validated local protocol.",
                "Plan glucose monitoring, nutrition and medication transition; ICU targets may differ.","ada",
                listOf("diabetes.glucose","diabetes.a1c","diabetes.meds").filter { c.v(it).isBlank() }.joinToString { it.substringAfter('.') })
            if((c.num("diabetes.glucose",10.0,1500.0) ?: 100.0) < 70)
                add("hypoglycemia","Diabetes","Promptly assess hypoglycemia","Glucose <70 mg/dL","Assess and treat using the local hypoglycemia protocol.",
                    "Specify repeat glucose checks and identify the cause.","Prevent recurrence during medication/nutrition changes.","ada")
            if(c.yes("diabetes.glp1")) add("glp1","Diabetes","Individualize GLP-1 planning","GLP-1 therapy selected",
                "Review GI symptoms, dose escalation, dose timing, indication and urgency.","Assess aspiration precautions and gastric ultrasound when appropriate.",
                "Coordinate restart with oral intake and the prescriber.","ada")
            if(c.yes("diabetes.sglt2")) add("sglt2","Diabetes","Review SGLT2 inhibitor timing","SGLT2 inhibitor selected",
                "For elective surgery verify agent-specific withholding (generally 3–4 days) with the prescriber.",
                "In urgent cases assess for ketoacidosis, including euglycemic presentations.","Document restart criteria and monitoring.","ada")
        }
        if(c.yes("ph")) add("ph","Pulmonary hypertension","Coordinate PH planning","Pulmonary hypertension selected",
            "Document group, severity, RV function and treatment; seek specialist input.","Plan RV-protective hemodynamic/ventilatory goals and essential therapy.",
            "Choose a destination able to provide anticipated support.","aha",
            listOf("ph.group","ph.severity","ph.rv","ph.treatment").filter{c.v(it).isBlank()}.joinToString{it.substringAfter('.')})
        val prompts = listOf(
            listOf("pulmonary","Pulmonary disease","Document control, recent exacerbations, infection, inhalers and baseline oxygen.","Individualize ventilation using PBW and measured mechanics.","Specify reassessment, support and escalation."),
            listOf("renal","Renal disease","Record renal trend, electrolytes, dialysis and volume status.","Review renal dosing, perfusion and fluid goals.","Plan renal/electrolyte monitoring and dialysis coordination."),
            listOf("liver","Liver disease","Review synthetic function, portal hypertension, encephalopathy, infection and nutrition.","Plan hemodynamics, temperature, glucose and bleeding assessment.","Specify neurological, renal and metabolic reassessment."),
            listOf("opioid","Chronic opioid use","Reconcile opioids, buprenorphine/methadone and the prescriber plan.","Document individualized multimodal and rescue analgesia.","Specify respiratory monitoring and baseline-therapy transition."),
            listOf("frailty","Frailty","Record mobility, assistance needs and a named validated assessment.","Plan positioning, pressure protection and warming.","Set mobility, nutrition and support goals."),
            listOf("anemia","Anemia","Record cause, symptoms, hemoglobin and optimization options.","Define blood availability and a patient-specific bleeding plan.","Specify reassessment and treatment."),
            listOf("nutrition","Nutrition","Record weight change, intake and nutrition-team needs.","Plan metabolic monitoring and fluid strategy.","Coordinate nutrition and recovery goals."),
            listOf("delirium","Delirium","Record baseline cognition, previous delirium, medication review and sensory aids.","Review modifiable precipitants.","Plan orientation, sleep, mobility and validated screening."),
            listOf("ponv","PONV","Record prior PONV/motion sickness, smoking and expected opioid exposure.","Select individualized prophylaxis.","Document rescue options from different classes.")
        )
        prompts.filter{c.yes(it[0])}.forEach { add(it[0],it[1],"Plan for "+it[1],it[1]+" selected",it[2],it[3],it[4],"local") }
        if(c.yes("mh")) add("mh","MH susceptibility","Prepare for MH susceptibility","Known/suspected MH susceptibility",
            "Verify history and arrange a trigger-free anesthetic with a prepared machine.","Check dantrolene access, monitoring and the local MH response protocol.",
            "Document course and postoperative monitoring requirements.","mh")
        if(c.yes("cardiac.symptoms")) add("cardiac","Cardiac","Assess active cardiac symptoms","New/active symptoms selected",
            "Clarify symptoms, urgency and appropriate specialist assessment.","Tailor monitoring and hemodynamic goals.",
            "Specify observation and indicated investigations.","aha")
        if(c.v("urgency")=="Elective" && c.v("complexity")=="Major / complex" && c.num("age",18.0,120.0)!=null &&
            c.v("procedure") in listOf("Knee replacement","Hip replacement","Whipple","TURP"))
            add("fbc","Investigations","Review a full blood count","Adult elective major/complex surgery",
                "NICE NG45 recommends FBC across ASA grades within its elective scope. Check existing results before requesting another.",
                "Use results in the blood/hemodynamic plan.","Reassess based on clinical course.","nice")
        return list
    }
}
