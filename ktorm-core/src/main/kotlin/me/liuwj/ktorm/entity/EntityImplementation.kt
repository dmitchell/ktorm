package me.liuwj.ktorm.entity

import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.defaultValue
import me.liuwj.ktorm.schema.kotlinProperty
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.reflect.jvm.kotlinFunction

/**
 * Created by vince on Jun 18, 2018.
 */
internal class EntityImplementation(
    var entityClass: KClass<*>,
    @Transient var fromTable: Table<*>?,
    @Transient var parent: EntityImplementation?
) : InvocationHandler, Serializable {

    var values = LinkedHashMap<String, Any?>()
    @Transient var changedProperties = LinkedHashSet<String>()

    companion object {
        private const val serialVersionUID = 1L
        private val defaultImplsCache: MutableMap<Method, Method> = Collections.synchronizedMap(WeakHashMap())
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return when (method.declaringClass.kotlin) {
            Any::class -> {
                when (method.name) {
                    "equals" -> this == args!![0]
                    "hashCode" -> this.hashCode()
                    "toString" -> this.toString()
                    else -> throw IllegalStateException("Unrecognized method: $method")
                }
            }
            Entity::class -> {
                when (method.name) {
                    "getEntityClass" -> this.entityClass
                    "getProperties" -> Collections.unmodifiableMap(this.values)
                    "flushChanges" -> this.doFlushChanges()
                    "discardChanges" -> this.doDiscardChanges()
                    "delete" -> this.doDelete()
                    "get" -> this.getProperty(args!![0] as String)
                    "set" -> this.setProperty(args!![0] as String, args[1])
                    else -> throw IllegalStateException("Unrecognized method: $method")
                }
            }
            else -> {
                handleMethodCall(proxy, method, args)
            }
        }
    }

    private fun handleMethodCall(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val ktProp = method.kotlinProperty
        if (ktProp != null) {
            val (prop, isGetter) = ktProp
            if (prop.isAbstract) {
                if (isGetter) {
                    val result = this.getProperty(prop.name)
                    if (result != null || prop.returnType.isMarkedNullable) {
                        return result
                    } else {
                        val defValue = (prop.returnType.classifier as KClass<*>).defaultValue
                        this.setProperty(prop.name, defValue)
                        return defValue
                    }
                } else {
                    this.setProperty(prop.name, args!![0])
                    return null
                }
            } else {
                return callDefaultImpl(proxy, method, args)
            }
        } else {
            val func = method.kotlinFunction
            if (func != null && !func.isAbstract) {
                return callDefaultImpl(proxy, method, args)
            } else {
                throw IllegalStateException("Unrecognized method: $method")
            }
        }
    }

    private fun callDefaultImpl(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val impl = defaultImplsCache.computeIfAbsent(method) {
            val cls = Class.forName(method.declaringClass.name + "\$DefaultImpls")
            cls.getMethod(method.name, method.declaringClass, *method.parameterTypes)
        }

        try {
            if (args == null) {
                return impl.invoke(null, proxy)
            } else {
                return impl.invoke(null, proxy, *args)
            }

        } catch (e: InvocationTargetException) {
            throw e.targetException

        } catch (e: Throwable) {
            throw e
        }
    }

    fun getProperty(name: String): Any? {
        return values[name]
    }

    fun setProperty(name: String, value: Any?, forceSet: Boolean = false) {
        if (!forceSet && isPrimaryKey(name) && name in values) {
            throw UnsupportedOperationException("Cannot modify the primary key value because it's already set to ${values[name]}")
        }

        values[name] = value
        changedProperties.add(name)
    }

    private fun writeObject(output: ObjectOutputStream) {
        output.writeUTF(entityClass.jvmName)
        output.writeObject(values)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readObject(input: ObjectInputStream) {
        entityClass = Class.forName(input.readUTF()).kotlin
        values = input.readObject() as LinkedHashMap<String, Any?>
        changedProperties = LinkedHashSet()
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is EntityImplementation -> this === other
            is Entity<*> -> this === other.implementation
            else -> false
        }
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return entityClass.simpleName + values
    }
}