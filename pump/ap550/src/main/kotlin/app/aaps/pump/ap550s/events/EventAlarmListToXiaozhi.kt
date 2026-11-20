package app.aaps.pump.ap550s.events

import app.aaps.core.interfaces.rx.events.Event

class EventAlarmListToXiaozhi(val alarmList: ByteArray) : Event()