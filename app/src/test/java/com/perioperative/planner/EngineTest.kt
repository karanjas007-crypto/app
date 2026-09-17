package com.perioperative.planner

import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    @Test fun elevatedA1cPromptsReviewOnlyAboveThreshold(){
        val c=Case(fields=mapOf("diabetes" to "Yes","diabetes.a1c" to "8.0"))
        assertFalse(Content.advice(c).any{it.id=="a1c-review"})
        assertTrue(Content.advice(c.set("diabetes.a1c","8.1")).any{it.id=="a1c-review"})
        assertFalse(Content.advice(c.set("diabetes.a1c","")).any{it.id=="a1c-review"})
    }
    @Test fun unknownIsNotNegative(){
        val s=Engine.score(Case(),Engine.rcri)
        assertEquals(6,s.missing);assertEquals(0,s.positive)
        assertEquals("Incomplete screening",Engine.osa(Case()))
        assertNull(Engine.pbw(Case()))
    }
    @Test fun incompleteOsaDoesNotBecomeLowRisk(){
        val c=Case(fields=Engine.stop.take(7).associateWith{"No"})
        assertEquals("Incomplete screening",Engine.osa(c))
    }
    @Test fun combinationOsaHighRisk(){
        val c=Case(fields=Engine.stop.associateWith{"No"}+mapOf("stop.snore" to "Yes","stop.tired" to "Yes","stop.male" to "Yes"))
        assertEquals("High screening risk",Engine.osa(c))
    }
    @Test fun pbwUsesHeightAndSexNotActualWeight(){
        val c=Case(fields=mapOf("age" to "50","height" to "170","weight" to "80","pbwSex" to "Female"))
        assertEquals(61.516,Engine.pbw(c)!!,0.0001)
        assertEquals(Engine.pbw(c)!!,Engine.pbw(c.set("weight","160"))!!,0.0001)
        assertNull(Engine.pbw(c.set("age","8")))
    }
    @Test fun concentrationArithmeticRejectsInvalidInputs(){
        assertEquals(2.4,Engine.volume(12.0,5.0)!!,0.0001)
        assertNull(Engine.volume(12.0,0.0));assertNull(Engine.volume(Double.NaN,5.0))
        assertNull(Engine.volume(12.0,Double.POSITIVE_INFINITY))
    }
    @Test fun timeRequiresOffsetAndValidDate(){
        assertNull(Engine.time("2026-09-01T08:00"))
        assertNull(Engine.time("2026-02-30T08:00Z"))
        assertEquals(Engine.time("2026-09-01T15:00Z"),Engine.time("2026-09-01T08:00-07:00"))
    }
    @Test fun assessmentRevisionInvalidatesReviewAndKeepsPlanText(){
        val c=Case(fields=mapOf("plan.reason" to "Original"),revision=4,reviewed=true)
        val updated=c.set("age","68")
        assertEquals(5,updated.revision);assertFalse(updated.reviewed);assertEquals("Original",updated.v("plan.reason"))
        assertEquals(4,c.set("plan.reason","Edited").revision)
    }
    @Test fun airwayRestrictionKeepsTechniqueOpenAndNICEHasScope(){
        val airway=Content.advice(Case(fields=mapOf("airway.opening" to "2"))).first{it.id=="airway-access"}
        assertTrue(airway.missing.isNotBlank());assertFalse(airway.text.contains("must use"))
        val base=mapOf("age" to "68","urgency" to "Elective","complexity" to "Major / complex")
        assertTrue(Content.advice(Case(fields=base+("procedure" to "Knee replacement"))).any{it.id=="fbc"})
        assertFalse(Content.advice(Case(fields=base+("procedure" to "CABG"))).any{it.id=="fbc"})
        assertFalse(Engine.rcriEligible(Case(fields=base+("procedure" to "CABG"))))
    }
}
