package com.hermeswebui.android

import com.google.common.truth.Truth.assertThat
import com.hermeswebui.android.data.HermesApiClient
import com.hermeswebui.android.data.SettingsRepository
import org.junit.Test

/** Regression guard for active-profile being derived from the id key, not the stale isActive row. */
class SettingsRepositoryProfilesTest {
    // Persisted JSON deliberately still marks A as isActive=true and B as false, mimicking the
    // stale-boolean state left behind when setActiveProfile(B) only updates the active-id key.
    private val twoProfiles = """
        [{"id":"A","name":"A","url":"https://a.example","createdAt":1,"isActive":true},
         {"id":"B","name":"B","url":"https://b.example","createdAt":2,"isActive":false}]
    """.trimIndent()

    @Test
    fun `active is derived from the active id, not the persisted isActive boolean`() {
        val profiles = SettingsRepository.parseProfiles(twoProfiles, activeId = "B")
        assertThat(profiles.first { it.id == "B" }.isActive).isTrue()
        assertThat(profiles.first { it.id == "A" }.isActive).isFalse()
    }

    @Test
    fun `no active id means no profile is active`() {
        val profiles = SettingsRepository.parseProfiles(twoProfiles, activeId = null)
        assertThat(profiles.none { it.isActive }).isTrue()
    }

    @Test
    fun `null or malformed json yields an empty list`() {
        assertThat(SettingsRepository.parseProfiles(null, null)).isEmpty()
        assertThat(SettingsRepository.parseProfiles("not json", "A")).isEmpty()
        assertThat(SettingsRepository.parseProfiles("[]", "A")).isEmpty()
    }

    @Test
    fun `proxy basic credentials are keyed by the exact page origin`() {
        val key = SettingsRepository.proxyBasicAuthKey("https://hermes.example.com/")
        assertThat(key).isNotNull()
        // Path, query, default port and host case do not change the origin.
        assertThat(SettingsRepository.proxyBasicAuthKey("https://HERMES.example.com:443/session/1?x=y")).isEqualTo(key)
        // Scheme, port and subdomain do: credentials are never offered to another origin.
        assertThat(SettingsRepository.proxyBasicAuthKey("http://hermes.example.com/")).isNotEqualTo(key)
        assertThat(SettingsRepository.proxyBasicAuthKey("https://hermes.example.com:8787/")).isNotEqualTo(key)
        assertThat(SettingsRepository.proxyBasicAuthKey("https://api.hermes.example.com/")).isNotEqualTo(key)
        // Unparseable or non-web URLs fail closed.
        assertThat(SettingsRepository.proxyBasicAuthKey("not a url")).isNull()
        assertThat(SettingsRepository.proxyBasicAuthKey("file:///sdcard/x")).isNull()
    }

    @Test
    fun `proxy basic credentials round trip and unreadable values read as absent`() {
        val credentials = HermesApiClient.BasicAuthCredentials(username = "alex", password = "p\"a:ss\n")
        val stored = SettingsRepository.encodeProxyBasicAuth(credentials)

        assertThat(SettingsRepository.decodeProxyBasicAuth(stored)).isEqualTo(credentials)
        assertThat(SettingsRepository.decodeProxyBasicAuth(null)).isNull()
        assertThat(SettingsRepository.decodeProxyBasicAuth("")).isNull()
        assertThat(SettingsRepository.decodeProxyBasicAuth("not json")).isNull()
        assertThat(SettingsRepository.decodeProxyBasicAuth("""{"username":"alex"}""")).isNull()
    }
}
