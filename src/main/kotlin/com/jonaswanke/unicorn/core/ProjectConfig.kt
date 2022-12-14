package com.jonaswanke.unicorn.core

import com.github.ajalt.clikt.core.NoSuchOption
import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import net.swiftzer.semver.SemVer
import java.net.URL

@Serializable
data class ProjectConfig(
    @Serializable(SemVerSerializer::class)
    val unicornVersion: SemVer,
    val name: String,
    val description: String? = null,
    @Serializable(UrlSerializer::class)
    val homepage: URL? = null,
    val license: License? = null,
    @Serializable(SemVerSerializer::class)
    val version: SemVer = SemVer(0, 0, 1),
    val categorization: CategorizationConfig = CategorizationConfig()
) {
    fun copyWithCategorizationValues(
        components: List<CategorizationConfig.ComponentConfig.Component> = categorization.component.values,
        priorities: List<CategorizationConfig.PriorityConfig.Priority> = categorization.priority.values,
        types: List<CategorizationConfig.TypeConfig.Type> = categorization.type.values
    ): ProjectConfig = copy(
        categorization = categorization.copy(
            component = categorization.component.copy(
                values = components.sortedBy { it.name }
            ),
            priority = categorization.priority.copy(
                values = priorities
            ),
            type = categorization.type.copy(
                values = types.sortedBy { it.name }
            )
        )
    )

    @Serializable(with = License.Serializer::class)
    enum class License(val keyword: String) {
        NONE("none"),
        APACHE_2_0("apache-2.0"),
        MIT("mit");

        companion object {
            fun fromKeywordOrNull(keyword: String): License? {
                return values().firstOrNull { it.keyword.equals(keyword, ignoreCase = true) }
            }
        }

        override fun toString() = keyword

        @kotlinx.serialization.Serializer(forClass = License::class)
        object Serializer : KSerializer<License> {
            override val descriptor: SerialDescriptor = StringDescriptor

            override fun serialize(encoder: Encoder, obj: License) = encoder.encodeString(obj.keyword)

            override fun deserialize(decoder: Decoder): License {
                val keyword = decoder.decodeString()
                return fromKeywordOrNull(keyword)
                    ?: error(
                        "License $keyword from project config is unknown to Unicorn. " +
                                "Known licenses: ${values().joinToString()}"
                    )
            }
        }
    }

    @Serializable
    data class CategorizationConfig(
        val component: ComponentConfig = ComponentConfig(),
        val priority: PriorityConfig = PriorityConfig(),
        val type: TypeConfig = TypeConfig()
    ) {
        @Serializable
        data class TypeConfig(
            override val values: List<Type> = listOf(
                Type("feat", ":tada: New Features"),
                Type("change", "??? Changes"),
                Type("fix", ":bug: Bug Fixes"),
                Type("docs", ":scroll: Documentation updates"),
                Type("refactor", ":building_construction: Refactoring"),
                Type("build", ":package: Build & CI")
            ),
            override val labels: LabelConfig = LabelConfig(
                color = "c5def5",
                prefix = "T: ",
                descriptionPrefix = "Type: "
            )
        ) : Categorization<TypeConfig.Type>() {
            override val name = "type"

            @Serializable
            data class Type(
                override val name: String,
                override val description: String? = null,
                val versionBump: VersionBump? = null
            ) : CategorizationValue {
                @Serializable(with = VersionBump.Serializer::class)
                enum class VersionBump {
                    MAJOR, BREAKING, MINOR, FEATURE, PATCH, FIX;

                    @kotlinx.serialization.Serializer(forClass = VersionBump::class)
                    object Serializer : KSerializer<VersionBump> {
                        override val descriptor: SerialDescriptor = StringDescriptor

                        override fun serialize(encoder: Encoder, obj: VersionBump) =
                            encoder.encodeString(obj.name.toLowerCase())

                        override fun deserialize(decoder: Decoder): VersionBump {
                            val value = decoder.decodeString()
                            return values().firstOrNull { it.name.equals(value, ignoreCase = true) }
                                ?: error(
                                    "VersionBump $value from project config is unknown to Unicorn. " +
                                            "Known values: ${values().joinToString()}"
                                )
                        }
                    }
                }
            }
        }

        @Serializable
        data class ComponentConfig(
            override val values: List<Component> = emptyList(),
            override val labels: LabelConfig = LabelConfig(
                color = "c2e0c6",
                prefix = "C: ",
                descriptionPrefix = "Component: "
            )
        ) : Categorization<ComponentConfig.Component>() {
            override val name = "component"

            @Serializable
            data class Component(
                override val name: String,
                override val description: String? = null,
                val paths: List<String> = emptyList()
            ) : CategorizationValue
        }

        @Serializable
        data class PriorityConfig(
            override val values: List<Priority> = listOf(
                Priority("1", "1 (Lowest)"),
                Priority("2", "2 (Low)"),
                Priority("3", "3 (Medium)"),
                Priority("4", "4 (High)"),
                Priority("5", "5 (Highest)")
            ),
            override val labels: LabelConfig = LabelConfig(
                color = "e5b5ff",
                prefix = "P: ",
                descriptionPrefix = "Priority: "
            )
        ) : Categorization<PriorityConfig.Priority>() {
            override val name = "priority"

            @Serializable
            data class Priority(
                override val name: String,
                override val description: String? = null
            ) : CategorizationValue
        }

        interface CategorizationValue {
            val name: String
            val description: String?
        }
    }

    @Serializable
    data class LabelConfig(
        val color: String = "cfd3d7",
        val prefix: String = "",
        val descriptionPrefix: String = ""
    )
}


abstract class Categorization<V : ProjectConfig.CategorizationConfig.CategorizationValue> {
    @Transient
    abstract val name: String
    abstract val values: List<V>
    abstract val labels: ProjectConfig.LabelConfig

    val resolvedValues: List<ResolvedValue<V>> by lazy { values.map { ResolvedValue(this, it) } }

    operator fun contains(name: String): Boolean = getOrNull(name) != null
    operator fun get(name: String): ResolvedValue<V> {
        return getOrNull(name)
            ?: throw NoSuchOption(name, values.map { it.name })
    }

    fun getOrNull(name: String): ResolvedValue<V>? {
        return resolvedValues.firstOrNull { it.name == name } ?: resolvedValues.firstOrNull { it.fullName == name }
    }

    data class ResolvedValue<V : ProjectConfig.CategorizationConfig.CategorizationValue>(
        val categorization: Categorization<V>,
        val value: V
    ) {
        val name: String = value.name
        val fullName = categorization.labels.prefix + name
        val description: String? = value.description
        val fullDescription: String = categorization.labels.descriptionPrefix + (description ?: name)
        val color: String = categorization.labels.color
    }
}
