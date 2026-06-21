package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun testSystemVersionDeserialization() {
        val fixture = """
            {
              "arch": "amd64",
              "codename": "Fermium Flea",
              "isBeta": false,
              "isCandidate": false,
              "isRelease": true,
              "longVersion": "syncthing v1.6.1 \"Fermium Flea\" (go1.14.4 linux-amd64)",
              "os": "linux",
              "version": "v1.6.1",
              "currentVersion": "v1.6.1",
              "latestVersion": "v1.6.1",
              "upgradeAvailable": false
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SystemVersion>(fixture)
        assertEquals("v1.6.1", parsed.currentVersion)
        assertEquals("syncthing v1.6.1 \"Fermium Flea\" (go1.14.4 linux-amd64)", parsed.longVersion)
        assertEquals("v1.6.1", parsed.latestVersion)
        assertFalse(parsed.upgradeAvailable)
        assertEquals("amd64", parsed.arch)
        assertEquals("linux", parsed.os)

        val serialized = json.encodeToString(SystemVersion.serializer(), parsed)
        assertTrue(serialized.contains("currentVersion"))
        assertTrue(serialized.contains("longVersion"))
    }

    @Test
    fun testSystemStatusDeserialization() {
        val fixture = """
            {
              "alloc": 30618136,
              "sys": 40000000,
              "myID": "53STGR7-YBM6FCX-PAZ2RHM-YPY6OEJ-WYHVZO7-PCKQRCK-PZLTP7T-434XCAD",
              "uptime": 3600,
              "guiAddressUsed": "127.0.0.1:8384",
              "startTime": "2026-06-21T05:11:00Z",
              "cpuPercent": 0.0,
              "numCPU": 8,
              "numFolders": 3,
              "numDevices": 2,
              "numConnected": 1,
              "goroutines": 45,
              "discoveryErrors": {
                "global@https://discovery.syncthing.net": "some error"
              },
              "listenAddresses": [
                "tcp://0.0.0.0:22000"
              ],
              "connectionServiceStatus": {
                "relay": {
                  "error": null,
                  "lanAddresses": ["relay://1.2.3.4:443"],
                  "wanAddresses": ["relay://1.2.3.4:443"]
                }
              },
              "relaysEnabled": true,
              "tilde": "/home/user",
              "pathSeparator": "/"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SystemStatus>(fixture)
        assertEquals(30618136L, parsed.alloc)
        assertEquals(40000000L, parsed.sys)
        assertEquals("53STGR7-YBM6FCX-PAZ2RHM-YPY6OEJ-WYHVZO7-PCKQRCK-PZLTP7T-434XCAD", parsed.myID)
        assertEquals(3600L, parsed.uptime)
        assertEquals("127.0.0.1:8384", parsed.guiAddressUsed)
        assertEquals("2026-06-21T05:11:00Z", parsed.startTime)
        assertEquals(0.0, parsed.cpuPercent)
        assertEquals(8, parsed.numCPU)
        assertEquals(3, parsed.numFolders)
        assertEquals(2, parsed.numDevices)
        assertEquals(1, parsed.numConnected)
        assertEquals(45, parsed.goroutines)
        assertEquals("some error", parsed.discoveryErrors["global@https://discovery.syncthing.net"])
        assertEquals("tcp://0.0.0.0:22000", parsed.listenAddresses.first())
        assertTrue(parsed.relaysEnabled)
        assertEquals("/home/user", parsed.tilde)
        assertEquals("/", parsed.pathSeparator)

        val relayStatus = parsed.connectionServiceStatus["relay"]
        assertNotNull(relayStatus)
        assertEquals("relay://1.2.3.4:443", relayStatus.lanAddresses.first())
    }

    @Test
    fun testFolderDbStatusDeserialization() {
        val fixture = """
            {
              "globalBytes": 1000,
              "localBytes": 900,
              "needBytes": 100,
              "state": "syncing",
              "inSyncBytes": 900,
              "needDeletes": 5,
              "sequence": 12345,
              "stateChanged": "2026-06-21T05:11:00Z",
              "version": 99
            }
        """.trimIndent()

        val parsed = json.decodeFromString<FolderDbStatus>(fixture)
        assertEquals(1000L, parsed.globalBytes)
        assertEquals(900L, parsed.localBytes)
        assertEquals(100L, parsed.needBytes)
        assertEquals("syncing", parsed.state)
        assertEquals(900L, parsed.inSyncBytes)
        assertEquals(5L, parsed.needDeletes)
        assertEquals(12345L, parsed.sequence)
        assertEquals("2026-06-21T05:11:00Z", parsed.stateChanged)
        assertEquals(99L, parsed.version)
    }

    @Test
    fun testDeviceCompletionDeserialization() {
        val fixture = """
            {
              "completion": 95.5,
              "globalBytes": 5000,
              "needBytes": 250,
              "device": "DEV-ID-1234",
              "folder": "folder-abc",
              "remoteState": "valid"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<DeviceCompletion>(fixture)
        assertEquals(95.5, parsed.completion)
        assertEquals(5000L, parsed.globalBytes)
        assertEquals(250L, parsed.needBytes)
        assertEquals("DEV-ID-1234", parsed.device)
        assertEquals("folder-abc", parsed.folder)
        assertEquals("valid", parsed.remoteState)
    }

    @Test
    fun testConnectionsResponseDeserialization() {
        val fixture = """
            {
              "connections": {
                "DEV-ID-1234": {
                  "address": "192.168.1.50:22000",
                  "at": "2026-06-21T05:11:00Z",
                  "clientVersion": "v1.20.0",
                  "connected": true,
                  "type": "tcp-client",
                  "inBytesTotal": 100000,
                  "outBytesTotal": 200000
                }
              },
              "total": {
                "inBytesTotal": 100000,
                "outBytesTotal": 200000
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ConnectionsResponse>(fixture)
        assertEquals(100000L, parsed.total.inBytesTotal)
        assertEquals(200000L, parsed.total.outBytesTotal)

        val devConn = parsed.connections["DEV-ID-1234"]
        assertNotNull(devConn)
        assertEquals("192.168.1.50:22000", devConn.address)
        assertEquals("2026-06-21T05:11:00Z", devConn.at)
        assertEquals("v1.20.0", devConn.clientVersion)
        assertTrue(devConn.connected)
        assertEquals("tcp-client", devConn.type)
    }

    @Test
    fun testFolderDeserialization() {
        val fixture = """
            {
              "id": "folder-1",
              "label": "My Folder",
              "path": "/path/to/folder",
              "type": "sendreceive",
              "paused": false,
              "rescanIntervalS": 3600,
              "fsWatcherEnabled": true,
              "devices": [
                { "deviceID": "DEV-ID-1234" }
              ],
              "versioning": {
                "type": "simple",
                "params": {
                  "keep": "5"
                }
              }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<Folder>(fixture)
        assertEquals("folder-1", parsed.id)
        assertEquals("My Folder", parsed.label)
        assertEquals("/path/to/folder", parsed.path)
        assertEquals("sendreceive", parsed.type)
        assertFalse(parsed.paused)
        assertEquals(3600, parsed.rescanIntervalS)
        assertTrue(parsed.fsWatcherEnabled)
        assertEquals("DEV-ID-1234", parsed.devices.first().deviceID)
        assertEquals("simple", parsed.versioning.type)
        assertEquals("5", parsed.versioning.params["keep"])
    }

    @Test
    fun testDeviceDeserialization() {
        val fixture = """
            {
              "deviceID": "DEV-ID-1234",
              "name": "My Remote Device",
              "addresses": ["tcp://192.168.1.50:22000"],
              "paused": false,
              "introducer": false,
              "autoAcceptFolders": true,
              "untrusted": false
            }
        """.trimIndent()

        val parsed = json.decodeFromString<Device>(fixture)
        assertEquals("DEV-ID-1234", parsed.deviceID)
        assertEquals("My Remote Device", parsed.name)
        assertEquals("tcp://192.168.1.50:22000", parsed.addresses.first())
        assertFalse(parsed.paused)
        assertFalse(parsed.introducer)
        assertTrue(parsed.autoAcceptFolders)
        assertFalse(parsed.untrusted)
    }

    @Test
    fun testSystemLogDeserialization() {
        val fixture = """
            {
              "messages": [
                {
                  "when": "2026-06-21T05:11:00Z",
                  "message": "Syncthing started"
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SystemLog>(fixture)
        val entry = parsed.messages.first()
        assertEquals("2026-06-21T05:11:00Z", entry.timestamp)
        assertEquals("Syncthing started", entry.message)
    }
}
