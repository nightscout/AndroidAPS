package app.aaps.pump.insight.exceptions

class ConnectionFailedException(val durationOfConnectionAttempt: Long) : InsightException()