package com.impca201.noter.data.store

/**
 * Names of the stored files. Only [SETTINGS] goes into Android's backup and phone-to-phone
 * transfer (`res/xml/data_extraction_rules.xml`, checked by `BackupRulesTest`).
 */
object DataFiles {
    /** Databases (name, icon, chip property), pins, custom order, theme. Backed up. */
    const val SETTINGS = "backed_up_settings"

    /** The Notion API key (EncryptedSharedPreferences). Never leaves this phone. */
    const val API_KEY = "notion_key_encrypted_prefs"

    /** Used only when the Android keystore fails. Never leaves this phone. */
    const val API_KEY_FALLBACK = "notion_key_plain_fallback_prefs"

    /** Folder (inside the no-backup folder) with copies of Notion data and unsent changes. */
    const val CACHE_DIR = "notion_cache"
}
