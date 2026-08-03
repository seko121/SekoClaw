package com.sikoclaw.app.floating
import com.sikoclaw.app.ui.chat.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
object SharedChatBus {
    private val _messages=MutableStateFlow<List<ChatMessage>>(emptyList()); val messages=_messages.asStateFlow()
    private val _status=MutableStateFlow("Ready"); val status=_status.asStateFlow()
    @Volatile var sendHandler:((String)->Unit)?=null
    @Volatile var taskHandler:((String)->Unit)?=null
    @Volatile var stopHandler:(()->Unit)?=null
    @Volatile var attachmentHandler:((com.sikoclaw.app.ui.chat.ChatAttachment)->Boolean)?=null
    fun publish(v:List<ChatMessage>){_messages.value=v.takeLast(20)}
    fun publishStatus(v:String){_status.value=v}
    fun send(v:String)=sendHandler?.let{it(v);true}?:false
    fun sendTask(v:String)=taskHandler?.let{it(v);true}?:false
    fun offerAttachment(v:com.sikoclaw.app.ui.chat.ChatAttachment)=attachmentHandler?.invoke(v)?:false
    fun stop(){stopHandler?.invoke()}
}
