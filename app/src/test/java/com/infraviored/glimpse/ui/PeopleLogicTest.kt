package com.infraviored.glimpse.ui

import com.infraviored.glimpse.data.Person
import com.infraviored.glimpse.ui.people.mergeTarget
import com.infraviored.glimpse.ui.people.sortPeople
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

class PeopleLogicTest {

    private val collator = Collator.getInstance(Locale.GERMAN)
    private val people = listOf(
        Person("1", "Anna", count = 10),
        Person("2", "Ben", count = 50),
        Person("3", "Ölaf", count = 5, favorite = true),
        Person("4", "Hidden", count = 99, hidden = true),
        Person("5", "", count = 70),
    )

    @Test
    fun favoritesFirstThenMostPictures() {
        assertEquals(listOf("3", "2", "1"), sortPeople(people, "", false, collator).map { it.id })
    }

    @Test
    fun hiddenOnlyWhenAsked() {
        assertEquals(listOf("3", "4", "2", "1"), sortPeople(people, "", true, collator).map { it.id })
    }

    @Test
    fun filteredByNameAlphabetically() {
        assertEquals(listOf("1", "2"), sortPeople(people, "n", false, collator).map { it.id })
    }

    @Test
    fun mergeKeepsTheNamedOneWithMostPictures() {
        assertEquals("1", mergeTarget(listOf(people[4], people[0]))?.id)
        assertEquals("2", mergeTarget(listOf(people[0], people[1]))?.id)
    }
}
