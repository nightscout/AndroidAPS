package app.aaps.plugins.sync.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class BaseUrl

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ClientId

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ClientSecret

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class RedirectUrl

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class AuthUrl