package com.x8bit.bitwarden.data.auth.datasource.disk

import androidx.core.content.edit
import app.cash.turbine.test
import com.x8bit.bitwarden.data.auth.datasource.disk.model.AccountJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.EnvironmentUrlDataJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.ForcePasswordResetReason
import com.x8bit.bitwarden.data.auth.datasource.disk.model.UserStateJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.KdfTypeJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.KeyConnectorUserDecryptionOptionsJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.TrustedDeviceUserDecryptionOptionsJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.UserDecryptionOptionsJson
import com.x8bit.bitwarden.data.platform.base.FakeSharedPreferences
import com.x8bit.bitwarden.data.platform.datasource.network.di.PlatformNetworkModule
import com.x8bit.bitwarden.data.vault.datasource.network.model.createMockOrganization
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthDiskSourceTest {
    private val fakeEncryptedSharedPreferences = FakeSharedPreferences()
    private val fakeSharedPreferences = FakeSharedPreferences()

    private val json = PlatformNetworkModule.providesJson()

    private val authDiskSource = AuthDiskSourceImpl(
        encryptedSharedPreferences = fakeEncryptedSharedPreferences,
        sharedPreferences = fakeSharedPreferences,
        json = json,
    )

    @Test
    fun `uniqueAppId should generate a new ID and update SharedPreferences if none exists`() {
        val rememberedUniqueAppIdKey = "bwPreferencesStorage:appId"

        // Assert that the SharedPreferences are empty
        assertNull(fakeSharedPreferences.getString(rememberedUniqueAppIdKey, null))

        // Generate a new uniqueAppId and retrieve it
        val newId = authDiskSource.uniqueAppId

        // Ensure that the SharedPreferences were updated
        assertEquals(
            newId,
            fakeSharedPreferences.getString(rememberedUniqueAppIdKey, null),
        )
    }

    @Test
    fun `uniqueAppId should not generate a new ID if one exists`() {
        val rememberedUniqueAppIdKey = "bwPreferencesStorage:appId"
        val testId = "testId"

        // Update preferences to hold test value
        fakeSharedPreferences.edit { putString(rememberedUniqueAppIdKey, testId) }

        assertEquals(testId, authDiskSource.uniqueAppId)
    }

    @Test
    fun `rememberedEmailAddress should pull from and update SharedPreferences`() {
        val rememberedEmailKey = "bwPreferencesStorage:rememberedEmail"

        // Shared preferences and the repository start with the same value.
        assertNull(authDiskSource.rememberedEmailAddress)
        assertNull(fakeSharedPreferences.getString(rememberedEmailKey, null))

        // Updating the repository updates shared preferences
        authDiskSource.rememberedEmailAddress = "remembered@gmail.com"
        assertEquals(
            "remembered@gmail.com",
            fakeSharedPreferences.getString(rememberedEmailKey, null),
        )

        // Update SharedPreferences updates the repository
        fakeSharedPreferences.edit { putString(rememberedEmailKey, null) }
        assertNull(authDiskSource.rememberedEmailAddress)
    }

    @Test
    fun `userState should pull from and update SharedPreferences`() {
        val userStateKey = "bwPreferencesStorage:state"

        // Shared preferences and the repository start with the same value.
        assertNull(authDiskSource.userState)
        assertNull(fakeSharedPreferences.getString(userStateKey, null))

        // Updating the repository updates shared preferences
        authDiskSource.userState = USER_STATE
        assertEquals(
            json.parseToJsonElement(
                USER_STATE_JSON,
            ),
            json.parseToJsonElement(
                fakeSharedPreferences.getString(userStateKey, null)!!,
            ),
        )

        // Update SharedPreferences updates the repository
        fakeSharedPreferences.edit { putString(userStateKey, null) }
        assertNull(authDiskSource.userState)
    }

    @Test
    fun `userStateFlow should react to changes in userState`() = runTest {
        authDiskSource.userStateFlow.test {
            // The initial values of the Flow and the property are in sync
            assertNull(authDiskSource.userState)
            assertNull(awaitItem())

            // Updating the repository updates shared preferences
            authDiskSource.userState = USER_STATE
            assertEquals(USER_STATE, awaitItem())
        }
    }

    @Test
    fun `clearData should clear all necessary data for the given user`() {
        val userId = "userId"

        authDiskSource.storeLastActiveTimeMillis(
            userId = userId,
            lastActiveTimeMillis = 123456789L,
        )
        authDiskSource.storeUserKey(userId = userId, userKey = "userKey")
        authDiskSource.storeUserAutoUnlockKey(
            userId = userId,
            userAutoUnlockKey = "userAutoUnlockKey",
        )
        authDiskSource.storePrivateKey(userId = userId, privateKey = "privateKey")
        authDiskSource.storeOrganizationKeys(
            userId = userId,
            organizationKeys = mapOf("organizationId" to "key"),
        )
        authDiskSource.storeOrganizations(
            userId = userId,
            organizations = listOf(createMockOrganization(1)),
        )

        authDiskSource.clearData(userId = userId)

        assertNull(authDiskSource.getLastActiveTimeMillis(userId = userId))
        assertNull(authDiskSource.getUserKey(userId = userId))
        assertNull(authDiskSource.getUserAutoUnlockKey(userId = userId))
        assertNull(authDiskSource.getPrivateKey(userId = userId))
        assertNull(authDiskSource.getOrganizationKeys(userId = userId))
        assertNull(authDiskSource.getOrganizations(userId = userId))
    }

    @Test
    fun `getLastActiveTimeMillis should pull from SharedPreferences`() {
        val lastActiveTimeBaseKey = "bwPreferencesStorage:lastActiveTime"
        val mockUserId = "mockUserId"
        val mockLastActiveTime = 123456789L
        fakeSharedPreferences
            .edit {
                putLong(
                    "${lastActiveTimeBaseKey}_$mockUserId",
                    mockLastActiveTime,
                )
            }
        val actual = authDiskSource.getLastActiveTimeMillis(userId = mockUserId)
        assertEquals(
            mockLastActiveTime,
            actual,
        )
    }

    @Test
    fun `storeLastActiveTimeMillis for non-null values should update SharedPreferences`() {
        val lastActiveTimeBaseKey = "bwPreferencesStorage:lastActiveTime"
        val mockUserId = "mockUserId"
        val mockLastActiveTime = 123456789L
        authDiskSource.storeLastActiveTimeMillis(
            userId = mockUserId,
            lastActiveTimeMillis = mockLastActiveTime,
        )
        val actual = fakeSharedPreferences
            .getLong(
                "${lastActiveTimeBaseKey}_$mockUserId",
                0L,
            )
        assertEquals(
            mockLastActiveTime,
            actual,
        )
    }

    @Test
    fun `storeLastActiveTimeMillis for null values should clear SharedPreferences`() {
        val lastActiveTimeBaseKey = "bwPreferencesStorage:lastActiveTime"
        val mockUserId = "mockUserId"
        val mockLastActiveTime = 123456789L
        val lastActiveTimeKey = "${lastActiveTimeBaseKey}_$mockUserId"
        fakeSharedPreferences
            .edit {
                putLong(lastActiveTimeKey, mockLastActiveTime)
            }
        assertTrue(fakeSharedPreferences.contains(lastActiveTimeKey))
        authDiskSource.storeLastActiveTimeMillis(
            userId = mockUserId,
            lastActiveTimeMillis = null,
        )
        assertFalse(fakeSharedPreferences.contains(lastActiveTimeKey))
    }

    @Test
    fun `getUserKey should pull from SharedPreferences`() {
        val userKeyBaseKey = "bwPreferencesStorage:masterKeyEncryptedUserKey"
        val mockUserId = "mockUserId"
        val mockUserKey = "mockUserKey"
        fakeSharedPreferences
            .edit {
                putString(
                    "${userKeyBaseKey}_$mockUserId",
                    mockUserKey,
                )
            }
        val actual = authDiskSource.getUserKey(userId = mockUserId)
        assertEquals(
            mockUserKey,
            actual,
        )
    }

    @Test
    fun `storeUserKey should update SharedPreferences`() {
        val userKeyBaseKey = "bwPreferencesStorage:masterKeyEncryptedUserKey"
        val mockUserId = "mockUserId"
        val mockUserKey = "mockUserKey"
        authDiskSource.storeUserKey(
            userId = mockUserId,
            userKey = mockUserKey,
        )
        val actual = fakeSharedPreferences
            .getString(
                "${userKeyBaseKey}_$mockUserId",
                null,
            )
        assertEquals(
            mockUserKey,
            actual,
        )
    }

    @Test
    fun `getPrivateKey should pull from SharedPreferences`() {
        val privateKeyBaseKey = "bwPreferencesStorage:encPrivateKey"
        val mockUserId = "mockUserId"
        val mockPrivateKey = "mockPrivateKey"
        fakeSharedPreferences
            .edit {
                putString(
                    "${privateKeyBaseKey}_$mockUserId",
                    mockPrivateKey,
                )
            }
        val actual = authDiskSource.getPrivateKey(userId = mockUserId)
        assertEquals(
            mockPrivateKey,
            actual,
        )
    }

    @Test
    fun `storePrivateKey should update SharedPreferences`() {
        val privateKeyBaseKey = "bwPreferencesStorage:encPrivateKey"
        val mockUserId = "mockUserId"
        val mockPrivateKey = "mockPrivateKey"
        authDiskSource.storePrivateKey(
            userId = mockUserId,
            privateKey = mockPrivateKey,
        )
        val actual = fakeSharedPreferences.getString(
            "${privateKeyBaseKey}_$mockUserId",
            null,
        )
        assertEquals(
            mockPrivateKey,
            actual,
        )
    }

    @Test
    fun `getUserAutoUnlockKey should pull from SharedPreferences`() {
        val userAutoUnlockKeyBaseKey = "bwSecureStorage:userKeyAutoUnlock"
        val mockUserId = "mockUserId"
        val mockUserAutoUnlockKey = "mockUserAutoUnlockKey"
        fakeEncryptedSharedPreferences
            .edit {
                putString(
                    "${userAutoUnlockKeyBaseKey}_$mockUserId",
                    mockUserAutoUnlockKey,
                )
            }
        val actual = authDiskSource.getUserAutoUnlockKey(userId = mockUserId)
        assertEquals(
            mockUserAutoUnlockKey,
            actual,
        )
    }

    @Test
    fun `storeUserAutoUnlockKey should update SharedPreferences`() {
        val userAutoUnlockKeyBaseKey = "bwSecureStorage:userKeyAutoUnlock"
        val mockUserId = "mockUserId"
        val mockUserAutoUnlockKey = "mockUserAutoUnlockKey"
        authDiskSource.storeUserAutoUnlockKey(
            userId = mockUserId,
            userAutoUnlockKey = mockUserAutoUnlockKey,
        )
        val actual = fakeEncryptedSharedPreferences
            .getString(
                "${userAutoUnlockKeyBaseKey}_$mockUserId",
                null,
            )
        assertEquals(
            mockUserAutoUnlockKey,
            actual,
        )
    }

    @Test
    fun `getPinProtectedUserKey should pull from SharedPreferences`() {
        val pinProtectedUserKeyBaseKey = "bwPreferencesStorage:pinKeyEncryptedUserKey"
        val mockUserId = "mockUserId"
        val mockPinProtectedUserKey = "mockPinProtectedUserKey"
        fakeSharedPreferences
            .edit {
                putString(
                    "${pinProtectedUserKeyBaseKey}_$mockUserId",
                    mockPinProtectedUserKey,
                )
            }
        val actual = authDiskSource.getPinProtectedUserKey(userId = mockUserId)
        assertEquals(
            mockPinProtectedUserKey,
            actual,
        )
    }

    @Test
    fun `storePinProtectedUserKey should update SharedPreferences`() {
        val pinProtectedUserKeyBaseKey = "bwPreferencesStorage:pinKeyEncryptedUserKey"
        val mockUserId = "mockUserId"
        val mockPinProtectedUserKey = "mockPinProtectedUserKey"
        authDiskSource.storePinProtectedUserKey(
            userId = mockUserId,
            pinProtectedUserKey = mockPinProtectedUserKey,
        )
        val actual = fakeSharedPreferences
            .getString(
                "${pinProtectedUserKeyBaseKey}_$mockUserId",
                null,
            )
        assertEquals(
            mockPinProtectedUserKey,
            actual,
        )
    }

    @Test
    fun `getEncryptedPin should pull from SharedPreferences`() {
        val encryptedPinBaseKey = "bwPreferencesStorage:protectedPin"
        val mockUserId = "mockUserId"
        val mockEncryptedPin = "mockEncryptedPin"
        fakeSharedPreferences
            .edit {
                putString(
                    "${encryptedPinBaseKey}_$mockUserId",
                    mockEncryptedPin,
                )
            }
        val actual = authDiskSource.getEncryptedPin(userId = mockUserId)
        assertEquals(
            mockEncryptedPin,
            actual,
        )
    }

    @Test
    fun `storeEncryptedPin should update SharedPreferences`() {
        val encryptedPinBaseKey = "bwPreferencesStorage:protectedPin"
        val mockUserId = "mockUserId"
        val mockEncryptedPin = "mockUserAutoUnlockKey"
        authDiskSource.storeEncryptedPin(
            userId = mockUserId,
            encryptedPin = mockEncryptedPin,
        )
        val actual = fakeSharedPreferences
            .getString(
                "${encryptedPinBaseKey}_$mockUserId",
                null,
            )
        assertEquals(
            mockEncryptedPin,
            actual,
        )
    }

    @Test
    fun `getOrganizationKeys should pull from SharedPreferences`() {
        val organizationKeysBaseKey = "bwPreferencesStorage:encOrgKeys"
        val mockUserId = "mockUserId"
        val mockOrganizationKeys = mapOf(
            "organizationId1" to "organizationKey1",
            "organizationId2" to "organizationKey2",
        )
        fakeSharedPreferences
            .edit {
                putString(
                    "${organizationKeysBaseKey}_$mockUserId",
                    """
                    {
                      "organizationId1": "organizationKey1",
                      "organizationId2": "organizationKey2"
                    }
                    """
                        .trimIndent(),
                )
            }
        val actual = authDiskSource.getOrganizationKeys(userId = mockUserId)
        assertEquals(
            mockOrganizationKeys,
            actual,
        )
    }

    @Test
    fun `putOrganizationKeys should update SharedPreferences`() {
        val organizationKeysBaseKey = "bwPreferencesStorage:encOrgKeys"
        val mockUserId = "mockUserId"
        val mockOrganizationKeys = mapOf(
            "organizationId1" to "organizationKey1",
            "organizationId2" to "organizationKey2",
        )
        authDiskSource.storeOrganizationKeys(
            userId = mockUserId,
            organizationKeys = mockOrganizationKeys,
        )
        val actual = fakeSharedPreferences.getString(
            "${organizationKeysBaseKey}_$mockUserId",
            null,
        )
        assertEquals(
            json.parseToJsonElement(
                """
                {
                  "organizationId1": "organizationKey1",
                  "organizationId2": "organizationKey2"
                }
                """
                    .trimIndent(),
            ),
            json.parseToJsonElement(requireNotNull(actual)),
        )
    }

    @Test
    fun `getOrganizations should pull from SharedPreferences`() {
        val organizationsBaseKey = "bwPreferencesStorage:organizations"
        val mockUserId = "mockUserId"
        val mockOrganizations = listOf(
            createMockOrganization(0),
            createMockOrganization(1),
        )
        val mockOrganizationsMap = mockOrganizations.associateBy { it.id }
        fakeSharedPreferences
            .edit {
                putString(
                    "${organizationsBaseKey}_$mockUserId",
                    json.encodeToString(mockOrganizationsMap),
                )
            }
        val actual = authDiskSource.getOrganizations(userId = mockUserId)
        assertEquals(
            mockOrganizations,
            actual,
        )
    }

    @Test
    fun `getOrganizationsFlow should react to changes in getOrganizations`() = runTest {
        val mockUserId = "mockUserId"
        val mockOrganizations = listOf(
            createMockOrganization(0),
            createMockOrganization(1),
        )
        authDiskSource.getOrganizationsFlow(userId = mockUserId).test {
            // The initial values of the Flow and the property are in sync
            assertNull(authDiskSource.getOrganizations(userId = mockUserId))
            assertNull(awaitItem())

            // Updating the repository updates shared preferences
            authDiskSource.storeOrganizations(
                userId = mockUserId,
                organizations = mockOrganizations,
            )
            assertEquals(mockOrganizations, awaitItem())
        }
    }

    @Test
    fun `storeOrganizations should update SharedPreferences`() {
        val organizationsBaseKey = "bwPreferencesStorage:organizations"
        val mockUserId = "mockUserId"
        val mockOrganizations = listOf(
            createMockOrganization(0),
            createMockOrganization(1),
        )
        val mockOrganizationsMap = mockOrganizations.associateBy { it.id }
        authDiskSource.storeOrganizations(
            userId = mockUserId,
            organizations = mockOrganizations,
        )
        val actual = fakeSharedPreferences.getString(
            "${organizationsBaseKey}_$mockUserId",
            null,
        )
        assertEquals(
            json.encodeToJsonElement(mockOrganizationsMap),
            json.parseToJsonElement(requireNotNull(actual)),
        )
    }
}

private const val USER_STATE_JSON = """
    {
      "activeUserId": "activeUserId",
      "accounts": {
        "activeUserId": {
          "profile": {
            "userId": "activeUserId",
            "email": "email",
            "emailVerified": true,
            "name": "name",
            "stamp": "stamp",
            "orgIdentifier": "organizationId",
            "avatarColor": "avatarColorHex",
            "hasPremiumPersonally": true,
            "forcePasswordResetReason": "adminForcePasswordReset",
            "kdfType": 1,
            "kdfIterations": 600000,
            "kdfMemory": 16,
            "kdfParallelism": 4,
            "accountDecryptionOptions": {
              "HasMasterPassword": true,
              "TrustedDeviceOption": {
                "EncryptedPrivateKey": "encryptedPrivateKey",
                "EncryptedUserKey": "encryptedUserKey",
                "HasAdminApproval": true,
                "HasLoginApprovingDevice": true,
                "HasManageResetPasswordPermission": true
              },
              "KeyConnectorOption": {
                "KeyConnectorUrl": "keyConnectorUrl"
              }
            }
          },
          "tokens": {
            "accessToken": "accessToken",
            "refreshToken": "refreshToken"
          },
          "settings": {
            "environmentUrls": {
              "base": "base",
              "api": "api",
              "identity": "identity",
              "icon": "icon",
              "notifications": "notifications",
              "webVault": "webVault",
              "events": "events"
            }
          }
        }
      }
    }
"""

private val USER_STATE = UserStateJson(
    activeUserId = "activeUserId",
    accounts = mapOf(
        "activeUserId" to AccountJson(
            profile = AccountJson.Profile(
                userId = "activeUserId",
                email = "email",
                isEmailVerified = true,
                name = "name",
                stamp = "stamp",
                organizationId = "organizationId",
                avatarColorHex = "avatarColorHex",
                hasPremium = true,
                forcePasswordResetReason = ForcePasswordResetReason.ADMIN_FORCE_PASSWORD_RESET,
                kdfType = KdfTypeJson.ARGON2_ID,
                kdfIterations = 600000,
                kdfMemory = 16,
                kdfParallelism = 4,
                userDecryptionOptions = UserDecryptionOptionsJson(
                    hasMasterPassword = true,
                    trustedDeviceUserDecryptionOptions = TrustedDeviceUserDecryptionOptionsJson(
                        encryptedPrivateKey = "encryptedPrivateKey",
                        encryptedUserKey = "encryptedUserKey",
                        hasAdminApproval = true,
                        hasLoginApprovingDevice = true,
                        hasManageResetPasswordPermission = true,
                    ),
                    keyConnectorUserDecryptionOptions = KeyConnectorUserDecryptionOptionsJson(
                        keyConnectorUrl = "keyConnectorUrl",
                    ),
                ),
            ),
            tokens = AccountJson.Tokens(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
            ),
            settings = AccountJson.Settings(
                environmentUrlData = EnvironmentUrlDataJson(
                    base = "base",
                    api = "api",
                    identity = "identity",
                    icon = "icon",
                    notifications = "notifications",
                    webVault = "webVault",
                    events = "events",
                ),
            ),
        ),
    ),
)
