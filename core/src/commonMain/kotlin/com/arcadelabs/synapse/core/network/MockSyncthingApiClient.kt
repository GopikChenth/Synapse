package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.domain.models.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MockSyncthingApiClient(
    private val localDeviceName: String = "This Device"
) : SyncthingApiClient {
    private val mockEngine = MockEngine {
        respond("OK", HttpStatusCode.OK)
    }
    private val client = HttpClient(mockEngine)

    private suspend fun dummyResponse(): HttpResponse {
        return client.request("")
    }

    private val foldersList = mutableListOf<Folder>()
    private val devicesList = mutableListOf<Device>()
    private val pendingDevicesMap = mutableMapOf<String, PendingDevice>(
        "PENDING-DEVICE-ID-99999-88888-ZZZZZ-YYYYY-XXXXX" to PendingDevice(
            time = "2026-06-22T12:30:00Z",
            name = "Mock Remote Laptop",
            address = "192.168.1.150:22000"
        )
    )

    private val pendingFoldersMap = mutableMapOf<String, PendingFolderOffer>(
        "mock-folder-id-abc" to PendingFolderOffer(
            offeredBy = mapOf(
                "PENDING-DEVICE-ID-99999-88888-ZZZZZ-YYYYY-XXXXX" to PendingFolder(
                    label = "Mock Shared Folder",
                    time = "2026-06-23T22:07:16Z",
                    receiveEncrypted = false,
                    remoteType = "sendreceive"
                )
            )
        )
    )

    init {
        devicesList.add(
            Device(
                deviceID = "MOCK-DEVICE-ID-12345-67890-ABCDE-FGHIJ-KLMNO",
                name = localDeviceName,
                addresses = listOf("dynamic")
            )
        )
    }

    private val logEntries = mutableListOf(
        LogEntry("2026-06-22T12:00:00Z", "syncthing v1.27.8 \"Goldfish\" (go1.22.4 windows-amd64) jonas@build.syncthing.net 2024-07-04 10:15:30 UTC"),
        LogEntry("2026-06-22T12:00:01Z", "My ID: MOCK-DEVICE-ID-12345-67890-ABCDE-FGHIJ-KLMNO"),
        LogEntry("2026-06-22T12:00:02Z", "Single point of entry: local addresses [tcp://0.0.0.0:22000]"),
        LogEntry("2026-06-22T12:00:05Z", "Ready to accept connections")
    )

    override suspend fun systemStatus(): SystemStatus {
        return SystemStatus(
            alloc = 25_000_000,
            sys = 45_000_000,
            myID = "MOCK-DEVICE-ID-12345-67890-ABCDE-FGHIJ-KLMNO",
            uptime = 1234,
            guiAddressUsed = "127.0.0.1:8384",
            startTime = "2026-06-22T12:00:00Z",
            cpuPercent = 1.5,
            numCPU = 8,
            numFolders = foldersList.size,
            numDevices = devicesList.size,
            numConnected = devicesList.count { !it.paused }
        )
    }

    override suspend fun systemVersion(): SystemVersion {
        return SystemVersion(
            currentVersion = "v1.27.8",
            longVersion = "syncthing v1.27.8 \"Goldfish\"",
            arch = "amd64",
            os = "windows"
        )
    }

    override suspend fun systemConfig(): SyncthingConfig {
        return SyncthingConfig(
            version = 37,
            folders = foldersList.toList(),
            devices = devicesList.toList()
        )
    }

    override suspend fun rawSystemConfig(): String {
        return "{}"
    }

    override suspend fun updateRawSystemConfig(configJson: String) {
        // Mock update
    }

    override suspend fun updateSystemConfig(config: SyncthingConfig) {
        foldersList.clear()
        foldersList.addAll(config.folders)
        
        devicesList.clear()
        devicesList.addAll(config.devices)
    }

    override suspend fun dbStatus(folderId: String): FolderDbStatus {
        return FolderDbStatus(
            globalBytes = 1024 * 1024 * 50,
            globalFiles = 150,
            globalDirectories = 25,
            localBytes = 1024 * 1024 * 50,
            localFiles = 150,
            localDirectories = 25,
            needBytes = 0,
            state = "idle",
            inSyncBytes = 1024 * 1024 * 50
        )
    }

    override suspend fun systemConnections(): ConnectionsResponse {
        val totalIn = 1234567L
        val totalOut = 7654321L
        val connectionMap = devicesList.associate { device ->
            device.deviceID to DeviceConnection(
                address = if (device.paused) "" else "192.168.1.100:22000",
                connected = !device.paused,
                clientVersion = "v1.27.8",
                inBytesTotal = 500000,
                outBytesTotal = 200000
            )
        }
        return ConnectionsResponse(
            connections = connectionMap,
            total = TotalConnection(inBytesTotal = totalIn, outBytesTotal = totalOut)
        )
    }

    override suspend fun dbCompletion(deviceId: String, folderId: String): DeviceCompletion {
        return DeviceCompletion(
            completion = 100.0,
            globalBytes = 1024 * 1024 * 50,
            needBytes = 0,
            device = deviceId,
            folder = folderId
        )
    }

    override suspend fun systemLog(): SystemLog {
        return SystemLog(messages = logEntries.toList())
    }

    override suspend fun pauseDevice(deviceId: String): HttpResponse {
        devicesList.find { it.deviceID == deviceId }?.let {
            val idx = devicesList.indexOf(it)
            devicesList[idx] = it.copy(paused = true)
        }
        return dummyResponse()
    }

    override suspend fun resumeDevice(deviceId: String): HttpResponse {
        devicesList.find { it.deviceID == deviceId }?.let {
            val idx = devicesList.indexOf(it)
            devicesList[idx] = it.copy(paused = false)
        }
        return dummyResponse()
    }

    override suspend fun restart(): HttpResponse {
        delay(1000)
        return dummyResponse()
    }

    override suspend fun shutdown(): HttpResponse {
        delay(500)
        return dummyResponse()
    }

    override suspend fun scan(folderId: String?): HttpResponse {
        delay(300)
        return dummyResponse()
    }

    override suspend fun getPendingDevices(): Map<String, PendingDevice> {
        return pendingDevicesMap.toMap()
    }

    override suspend fun dismissPendingDevice(deviceId: String): HttpResponse {
        pendingDevicesMap.remove(deviceId)
        return dummyResponse()
    }

    override suspend fun getPendingFolders(): Map<String, PendingFolderOffer> {
        return pendingFoldersMap.toMap()
    }

    override suspend fun dismissPendingFolder(
        folderId: String,
        deviceId: String,
        label: String,
        time: String
    ): HttpResponse {
        pendingFoldersMap.remove(folderId)
        return dummyResponse()
    }

    override suspend fun deleteFolder(folderId: String) {
        foldersList.removeAll { it.id == folderId }
    }

    override suspend fun deleteDevice(deviceId: String) {
        devicesList.removeAll { it.deviceID == deviceId }
    }

    private var mockOptions = ConfigOptions()
    private var mockGui = GuiConfig(apiKey = "mock-api-key-12345")

    override suspend fun getConfigOptions(): ConfigOptions = mockOptions

    override suspend fun updateConfigOptions(options: ConfigOptions) {
        mockOptions = options
    }

    override suspend fun getConfigGui(): GuiConfig = mockGui

    override suspend fun updateConfigGui(gui: GuiConfig) {
        mockGui = gui
    }

    override suspend fun getEvents(since: Int, limit: Int): List<Event> {
        return listOf(
            Event(
                id = 1,
                globalID = 1,
                type = "ItemFinished",
                time = "2026-06-25T20:10:00.000Z",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("item", "document.pdf")
                    put("folder", "default")
                    put("type", "file")
                    put("action", "update")
                }
            ),
            Event(
                id = 2,
                globalID = 2,
                type = "ItemFinished",
                time = "2026-06-25T20:12:00.000Z",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("item", "image.png")
                    put("folder", "photos")
                    put("type", "file")
                    put("action", "update")
                }
            ),
            Event(
                id = 3,
                globalID = 3,
                type = "ItemFinished",
                time = "2026-06-25T20:15:00.000Z",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("item", "old_config.txt")
                    put("folder", "config")
                    put("type", "file")
                    put("action", "delete")
                }
            )
        )
    }
}
