package com.thc.trincha

import android.util.Log
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RabbitMQ(private val QUEUE_NAME: String) {

    private val factory: ConnectionFactory = ConnectionFactory()
    private var connection: Connection? = null
    private var channel: Channel? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            setupConnection()
        }
    }

    private fun setupConnection() {
        try {
            factory.host = "192.168.1.15" // Substitua pelo IP do servidor RabbitMQ
            factory.port = 5672          // Porta padrão do RabbitMQ
            factory.username = "guest"  // Usuário RabbitMQ
            factory.password = "guest"  // Senha RabbitMQ
            connection = factory.newConnection()
            channel = connection?.createChannel()
            channel?.queueDeclare(QUEUE_NAME, true, false, false, null)
            Log.d("RABBITMQ", "Conexão com RabbitMQ estabelecida com sucesso!")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("ERROR setUpConnection", "Erro ao conectar ao RabbitMQ: ${e.message}")
        }
    }

    fun publishMessage(message: String) {
        try {
            channel?.basicPublish("", QUEUE_NAME, null, message.toByteArray())
            Log.d("RABBITMQ", "Mensagem publicada: $message")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("ERROR publishMessage", "Erro ao publicar mensagem: ${e.message}")
        }
    }

    fun consumeMessages(onMessageReceived: (String) -> Unit) {
        try {
            Log.d("RABBITMQ", "Iniciando o consumidor...") // Log de início
            // Define o DeliverCallback para tratar a mensagem recebida
            val deliverCallback = DeliverCallback { _, delivery ->
                val message = String(delivery.body)
                onMessageReceived(message)

                // Confirma o processamento da mensagem após o tratamento
                channel?.basicAck(delivery.envelope.deliveryTag, false)

                Log.d("RABBITMQ CONSUMER", "Mensagem recebida: $message")
            }

            // Define o CancelCallback para tratar o cancelamento do consumidor
            val cancelCallback = CancelCallback { tag ->
                Log.d("RABBITMQ CONSUMER", "Mensagem cancelada: $tag")
            }

            channel?.queueDeclare(QUEUE_NAME, true, false, false, null)
            // Consome as mensagens da fila com o DeliverCallback e CancelCallback
            channel?.basicConsume(QUEUE_NAME, false, deliverCallback, cancelCallback)
            Log.d("RABBITMQ", "Mensagem consumida com sucesso.") // Log de sucesso

        } catch (e: Exception) {
            e.printStackTrace()
            Log.d("ERROR consumeMessages", "Erro ao consumir mensagens: ${e.message}")
        }
    }

    fun closeConnection() {
        try {
            channel?.close()
            connection?.close()
            println("Conexão com RabbitMQ encerrada.")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Erro ao encerrar conexão: ${e.message}")
        }
    }
}
