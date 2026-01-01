package jp.kawai.ultrafocus.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CredentialEncryptedDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceProtectedDataStore
