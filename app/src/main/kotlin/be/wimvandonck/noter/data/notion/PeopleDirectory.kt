package be.wimvandonck.noter.data.notion

import be.wimvandonck.noter.data.api.arrayOrNull
import be.wimvandonck.noter.data.api.objOrNull
import be.wimvandonck.noter.data.api.stringOrNull
import be.wimvandonck.noter.data.model.DatabaseSchema
import kotlinx.serialization.json.JsonObject

/**
 * Notion's People directory (#92): the database every workspace has of its members' profiles.
 * Notion's API gives it as an ordinary data source without a mark, so it is told apart by the
 * properties Notion gives it and that can't be removed: "Membership Type" (Workspace owner,
 * Member, Guest, Deactivated, …) and an email property. Their names stay in English whatever
 * the workspace's language. It and its profiles are left out of Home and +; Search still finds them.
 */
object PeopleDirectory {
    private const val MEMBERSHIP = "Membership Type"
    private val CHOICES = setOf("select", "status")

    /** Membership types Notion uses; a profile has one of them. */
    private val MEMBERSHIPS = setOf("Workspace owner", "Workspace admin", "Membership admin", "Member", "Guest", "Deactivated")

    /**
     * The data sources among search [results] that can't be told from the search alone: Notion's
     * search may leave a data source's properties out. Each is asked for once ([checked]: by ID
     * without dashes) and the answer kept.
     */
    fun toCheck(results: List<JsonObject>, checked: Set<String>): List<String> =
        results.filter { it.stringOrNull("object") == "data_source" && it.objOrNull("properties").isNullOrEmpty() }
            .mapNotNull { it.stringOrNull("id") }
            .filter { it.replace("-", "") !in checked }
            .distinctBy { it.replace("-", "") }

    /** [json] (a data source or database from Notion) is the People directory. */
    fun isDirectory(json: JsonObject): Boolean {
        val properties = json.objOrNull("properties") ?: return false
        val membership = membershipOf(properties) ?: return false
        val options = membership.objOrNull(membership.stringOrNull("type").orEmpty())?.arrayOrNull("options").orEmpty()
        return options.any { (it as? JsonObject)?.stringOrNull("name") in MEMBERSHIPS }
    }

    /** [schema] (a data source as the app keeps it) is the People directory. */
    fun isDirectory(schema: DatabaseSchema): Boolean {
        val membership = schema.properties.firstOrNull { it.name == MEMBERSHIP && it.type in CHOICES } ?: return false
        return schema.properties.any { it.type == "email" } && membership.options.any { it.name in MEMBERSHIPS }
    }

    /** [page] is a member's profile in the People directory. */
    fun isProfile(page: JsonObject): Boolean {
        val properties = page.objOrNull("properties") ?: return false
        val membership = membershipOf(properties) ?: return false
        return membership.objOrNull(membership.stringOrNull("type").orEmpty())?.stringOrNull("name") in MEMBERSHIPS
    }

    /** The "Membership Type" choice, when [properties] also has an email property (both are Notion's own). */
    private fun membershipOf(properties: JsonObject): JsonObject? {
        val membership = properties.objOrNull(MEMBERSHIP) ?: return null
        if (membership.stringOrNull("type") !in CHOICES) return null
        val hasEmail = properties.values.any { (it as? JsonObject)?.stringOrNull("type") == "email" }
        return membership.takeIf { hasEmail }
    }
}
