package me.liuwj.ktorm.database

import me.liuwj.ktorm.BaseTest
import me.liuwj.ktorm.dsl.from
import me.liuwj.ktorm.dsl.joinReferencesAndSelect
import me.liuwj.ktorm.entity.Entity
import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.int
import org.junit.Test

/**
 * Created by vince on Dec 19, 2018.
 */
class CircularReferenceTest : BaseTest() {

    interface Foo1 : Entity<Foo1> {
        val id: Int
        val foo2: Foo2
    }

    interface Foo2 : Entity<Foo2> {
        val id: Int
        val foo3: Foo3
    }

    interface Foo3 : Entity<Foo3> {
        val id: Int
        val foo1: Foo1
    }

    object Foos1 : Table<Foo1>("foo1") {
        val id = int("id").primaryKey().bindTo { it.id }
        val r1 = int("r1").references(Foos2) { it.foo2 }
    }

    object Foos2 : Table<Foo2>("foo2") {
        val id = int("id").primaryKey().bindTo { it.id }
        val r2 = int("r2").references(Foos3) { it.foo3 }
    }

    object Foos3 : Table<Foo3>("foo3") {
        val id = int("id").primaryKey().bindTo { it.id }
        val r3 = int("r3").references(Foos1) { it.foo1 }
    }

    @Test
    fun testCircularReference() {
        try {
            database.from(Foos1).joinReferencesAndSelect()
            throw AssertionError("unexpected")

        } catch (e: ExceptionInInitializerError) {
            val ex = e.cause as IllegalStateException
            println(ex.message)
        }
    }

    interface Bar : Entity<Bar> {
        val id: Int
        val bar: Bar
    }

    object Bars : Table<Bar>("bar") {
        val id = int("id").primaryKey().bindTo { it.id }
        val r = int("r").references(Bars) { it.bar }
    }

    @Test
    fun test() {
        try {
            database.from(Bars).joinReferencesAndSelect()
            throw AssertionError("unexpected")

        } catch (e: ExceptionInInitializerError) {
            val ex = e.cause as IllegalStateException
            println(ex.message)
        }
    }
}