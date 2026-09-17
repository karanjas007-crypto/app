package com.perioperative.planner

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AnticoagulationTest {
    private val now=Instant.parse("2026-09-17T12:00:00Z")
    private fun at(s:String)=Engine.time(s)
    private fun patient(id:String="apix_high",extra:Map<String,String> = emptyMap()):Case {
        val r=Anticoagulation.regimens.first{it.id==id}
        return Case(fields=mapOf("age" to "65","anti.drug" to r.drug,"anti.regimen" to id,
            "anti.crcl" to "80","anti.aki" to "No","anti.other" to "No","anti.bleeding" to "No",
            "anti.last" to "2026-09-01T08:00Z","anti.insertion" to "2026-09-04T08:00Z",
            "anti.surgeryEnd" to "2026-09-04T10:00Z","anti.surgicalRisk" to "Low / moderate",
            "anti.catheter" to "No","anti.traumatic" to "No","anti.hemostasis" to "Yes","anti.thrombosis" to "Yes",
            "anti.over4days" to "No","anti.coagNormal" to "Yes","anti.inr" to "1.0","anti.inrNormal" to "Yes",
            "anti.labsCurrent" to "Yes","anti.loading" to "No")+extra)
    }
    private fun evaluate(c:Case)=Anticoagulation.evaluate(c,now)
    @Test fun sourcedHoldingIntervals(){
        val hours=mapOf("apix_low" to 36L,"apix_high" to 72L,"riva_low" to 24L,"riva_high" to 72L,
            "dabi_low" to 48L,"dabi_high" to 72L,"edox_high" to 72L,"enox_daily" to 12L,
            "enox_bid" to 12L,"enox_high" to 24L,"ufh_low" to 6L,"ufh_mid" to 12L,"ufh_high" to 24L,
            "ufh_iv" to 6L,"warfarin" to 120L,"clopidogrel" to 168L,"prasugrel" to 240L,"ticagrelor" to 120L)
        hours.forEach{(id,h)->
            val t=evaluate(patient(id));assertEquals(id,at("2026-09-01T08:00Z")!!.plusSeconds(h*3600),t.steps[0].earliest)
            assertEquals(id,t.steps[0].earliest,t.steps[1].earliest)
        }
    }
    @Test fun exactBoundaryAndOneSecondEarly(){
        val c=patient();assertEquals("Minimum timing criterion met",evaluate(c).steps[0].state)
        val early=evaluate(c.set("anti.insertion","2026-09-04T07:59:59Z"))
        assertEquals("Proposed time is too early",early.steps[0].state);assertNull(early.steps[3].earliest)
    }
    @Test fun basicInputsProduceWithholdingWithoutRestartData(){
        val c=patient().set("anti.insertion","").set("anti.surgeryEnd","").set("anti.catheter","")
        val t=evaluate(c);assertNotNull(t.steps[0].earliest);assertNull(t.steps[3].earliest)
    }
    @Test fun renalFunctionChangesIntervalsAndOutOfScopeNeverCalculates(){
        assertEquals(at("2026-09-06T08:00Z"),evaluate(patient("dabi_high",mapOf("anti.crcl" to "49"))).steps[0].earliest)
        assertEquals(at("2026-09-04T08:00Z"),evaluate(patient("dabi_high",mapOf("anti.crcl" to "50"))).steps[0].earliest)
        assertEquals(at("2026-09-02T14:00Z"),evaluate(patient("riva_low",mapOf("anti.crcl" to "29"))).steps[0].earliest)
        assertNull(evaluate(patient("riva_low",mapOf("anti.crcl" to "14"))).steps[0].earliest)
        for(e in listOf("anti.crcl" to "29","anti.crcl" to "NaN","anti.crcl" to "","anti.aki" to "Yes","anti.other" to "","anti.other" to "Yes","anti.bleeding" to "Yes","age" to "12"))
            assertTrue(e.toString(),evaluate(patient(extra=mapOf(e))).steps.all{it.earliest==null})
    }
    @Test fun afDoseReductionDoesNotSelectLowDosePath(){
        val c=patient(extra=mapOf("anti.dose" to "2.5 mg twice daily for AF"))
        assertEquals(at("2026-09-04T08:00Z"),evaluate(c).steps[0].earliest)
        assertEquals("",c.set("anti.drug","Rivaroxaban").v("anti.regimen"))
        assertTrue(evaluate(c.set("anti.drug","Rivaroxaban")).steps.all{it.earliest==null})
    }
    @Test fun futureOrInvalidLastAdministrationBlocks(){
        for(s in listOf("2026-09-18T08:00Z","2026-02-30T08:00Z","2026-09-01T08:00",""))
            assertNull(evaluate(patient(extra=mapOf("anti.last" to s))).steps[0].earliest)
    }
    @Test fun restartUsesSurgicalAndNeuraxialMaximum(){
        assertEquals(at("2026-09-05T10:00Z"),evaluate(patient()).steps[3].earliest)
        assertEquals(at("2026-09-07T10:00Z"),evaluate(patient(extra=mapOf("anti.surgicalRisk" to "High"))).steps[3].earliest)
        assertEquals(at("2026-09-10T08:00Z"),evaluate(patient(extra=mapOf("anti.surgical" to "2026-09-10T08:00Z"))).steps[3].earliest)
        for(k in listOf("anti.surgeryEnd","anti.hemostasis","anti.thrombosis","anti.traumatic","anti.surgicalRisk","anti.catheter"))
            assertNull(k,evaluate(patient(extra=mapOf(k to ""))).steps[3].earliest)
    }
    @Test fun catheterExposureHasIndependentRemovalAndRestartIntervals(){
        val c=patient(extra=mapOf("anti.catheter" to "Yes","anti.exposed" to "Yes","anti.insertion" to "2026-08-30T08:00Z",
            "anti.surgeryEnd" to "2026-08-30T10:00Z","anti.removal" to "2026-09-04T08:00Z"))
        val t=evaluate(c);assertNull(t.steps[0].earliest)
        assertEquals(at("2026-09-04T08:00Z"),t.steps[2].earliest)
        assertEquals(at("2026-09-05T08:00Z"),t.steps[3].earliest)
        assertNull(evaluate(c.set("anti.removal","2026-09-04T07:59:59Z")).steps[3].earliest)
        assertNull(evaluate(c.set("anti.insertion","2026-09-02T08:00Z")).steps[2].earliest)
        assertNull(evaluate(c.set("anti.exposed","No")).steps[3].earliest)
    }
    @Test fun staleExposureCannotBypassMissingCatheterOrEarlyNeedle(){
        val c=patient(extra=mapOf("anti.exposed" to "Yes","anti.insertion" to "2026-09-02T08:00Z"))
        assertNull(evaluate(c).steps[3].earliest)
        assertNull(evaluate(c.set("anti.catheter","Yes").set("anti.exposed","No").set("anti.removal","2026-09-05T08:00Z")).steps[3].earliest)
    }
    @Test fun traumaUsesDrugSpecificEvent(){
        assertEquals(at("2026-09-06T08:00Z"),evaluate(patient(extra=mapOf("anti.traumatic" to "Yes"))).steps[3].earliest)
        assertEquals(at("2026-09-05T10:00Z"),evaluate(patient("enox_daily",mapOf("anti.traumatic" to "Yes"))).steps[3].earliest)
        assertNull(evaluate(patient("dabi_high",mapOf("anti.traumatic" to "Yes"))).steps[3].earliest)
    }
    @Test fun lmwhTwiceDailyWaitsForNextCalendarDayAtEnteredOffset(){
        val c=patient("enox_bid",mapOf("anti.insertion" to "2026-09-04T06:00+05:30","anti.surgeryEnd" to "2026-09-04T08:00+05:30"))
        assertEquals(at("2026-09-05T00:00+05:30"),evaluate(c).steps[3].earliest)
    }
    @Test fun lmwhDailyRemovalAndNextDoseSpacing(){
        val c=patient("enox_daily",mapOf("anti.catheter" to "Yes","anti.exposed" to "Yes","anti.insertion" to "2026-08-30T08:00Z",
            "anti.surgeryEnd" to "2026-08-30T10:00Z","anti.removal" to "2026-09-01T20:00Z"))
        assertEquals(at("2026-09-01T20:00Z"),evaluate(c).steps[2].earliest)
        assertEquals(at("2026-09-02T08:00Z"),evaluate(c).steps[3].earliest)
        assertNull(evaluate(c.set("anti.regimen","enox_bid")).steps[3].earliest)
    }
    @Test fun heparinNeedsPlateletAndCoagulationChecks(){
        assertNull(evaluate(patient("ufh_low",mapOf("anti.over4days" to "Yes"))).steps[0].earliest)
        assertNotNull(evaluate(patient("ufh_low",mapOf("anti.over4days" to "Yes","anti.platelets" to "Yes"))).steps[0].earliest)
        assertNull(evaluate(patient("ufh_iv",mapOf("anti.coagNormal" to ""))).steps[0].earliest)
        assertNull(evaluate(patient("ufh_high")).steps[3].earliest)
    }
    @Test fun warfarinInsertionNeedsNormalizedInrButRemovalUsesDifferentCriterion(){
        assertNull(evaluate(patient("warfarin",mapOf("anti.inr" to "2"))).steps[0].earliest)
        val c=patient("warfarin",mapOf("anti.catheter" to "Yes","anti.exposed" to "Yes","anti.insertion" to "2026-08-30T08:00Z",
            "anti.surgeryEnd" to "2026-08-30T10:00Z","anti.removal" to "2026-09-01T20:00Z","anti.inr" to "1.4","anti.inrNormal" to "No"))
        assertEquals("INR criterion met; confirm removal plan",evaluate(c).steps[2].state)
        assertEquals(at("2026-09-01T20:00Z"),evaluate(c).steps[3].earliest)
        assertNull(evaluate(c.set("anti.inr","1.5")).steps[3].earliest)
        assertNull(evaluate(c.set("anti.removal","2026-08-31T20:00Z")).steps[3].earliest)
    }
    @Test fun antiplateletLoadingAddsSixHours(){
        val c=patient("clopidogrel",mapOf("anti.insertion" to "2026-09-08T08:00Z","anti.surgeryEnd" to "2026-09-08T10:00Z"))
        assertEquals(at("2026-09-08T10:00Z"),evaluate(c).steps[3].earliest)
        assertEquals(at("2026-09-08T14:00Z"),evaluate(c.set("anti.loading","Yes")).steps[3].earliest)
    }
    @Test fun aspirinDoesNotTreatHistoricalNeedleAsTooEarly(){
        val t=evaluate(patient("aspirin",mapOf("anti.last" to "")))
        assertEquals("No drug-specific withholding interval",t.steps[0].state)
        assertEquals(at("2026-09-04T10:00Z"),t.steps[3].earliest)
    }
}
