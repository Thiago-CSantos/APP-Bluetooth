package com.thc.trincha

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    var btfAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null


    private val factory = ConnectionFactory()
    private val QUEUE_NAME = "chatbot_messages"
    private var connection: Connection? = null
    private var channel: Channel? = null

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSION = 1
        const val ENABLE_BLUETOOTH_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)

        btfAdapter = BluetoothAdapter.getDefaultAdapter()

        if (this.btfAdapter == null) {
            Toast.makeText(this, "Bluetooth não está disponível", Toast.LENGTH_LONG).show()
            return
        }

        // Verificação da permissão do Bluetooth no Android 6.0+ (API 23 ou superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        }

        // Verifica o estado do Bluetooth
        if (btfAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth está ligado", Toast.LENGTH_LONG).show()
        } else {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }

        val pairedDevices = btfAdapter?.bondedDevices
        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                if (device.name.contains("KP-1025")) { // Substitua pelo nome ou endereço Bluetooth da sua impressora
                    connectToDevice(device)
                    break
                }
            }
        } else {
            Toast.makeText(this, "Nenhum dispositivo pareado encontrado", Toast.LENGTH_LONG).show()
        }

        // Configurando RabbitMQ para consumir mensagens
        setupRabbitMQ()
        // Iniciar consumo contínuo
        startConsumingMessages()

    }

    private fun setupRabbitMQ() {
        try {
            factory.host = "192.168.1.14" // IP do servidor RabbitMQ
            factory.port = 5672           // Porta padrão do RabbitMQ
            factory.username = "guest"   // Usuário RabbitMQ
            factory.password = "guest"   // Senha RabbitMQ
            Log.d("RABBITMQ", "Configuração de conexão concluída")
        } catch (e: Exception) {
            Log.e("RABBITMQ", "Erro ao configurar RabbitMQ: ${e.message}")
        }
    }

    private fun startConsumingMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    if (connection == null || channel == null || !connection!!.isOpen) {
                        // Estabelece nova conexão se não estiver conectada
                        connection = factory.newConnection()
                        channel = connection!!.createChannel()
                        channel!!.queueDeclare(QUEUE_NAME, true, false, false, null)
                        Log.d("RABBITMQ", "Conexão com RabbitMQ estabelecida")
                    }

                    // Configura callback para mensagens
                    val deliverCallback = DeliverCallback { _, delivery ->
                        val message = String(delivery.body)
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Mensagem recebida: $message",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Log.d("RABBITMQ", "Mensagem recebida: $message")
                        printData(message)
                        channel!!.basicAck(delivery.envelope.deliveryTag, false)
                    }

                    // Inicia consumo
                    channel!!.basicConsume(QUEUE_NAME, false, deliverCallback, CancelCallback {
                        Log.d("RABBITMQ", "Consumo cancelado")
                    })

                    // Aguarda indefinidamente até ocorrer erro
                    Thread.sleep(Long.MAX_VALUE)
                } catch (e: Exception) {
                    Log.e("RABBITMQ", "Erro ao consumir mensagens: ${e.message}")
                    // Aguarda antes de tentar reconectar
                    Thread.sleep(5000)
                }
            }
        }
    }

    private suspend fun consumeMessages(onMessageReceived: (String) -> Unit) {
        try {
            // Conexão e canal
            val connection = factory.newConnection()
            val channel = connection.createChannel()

            // Declaração da fila
            channel.queueDeclare(QUEUE_NAME, true, false, false, null)
            Log.d("RABBITMQ", "Conexão com RabbitMQ estabelecida")

            // Callback para mensagens recebidas
            val deliverCallback = DeliverCallback { _, delivery ->
                val message = String(delivery.body)
                onMessageReceived(message)
                channel.basicAck(delivery.envelope.deliveryTag, false)
            }

            // Callback para mensagens canceladas
            val cancelCallback = CancelCallback { tag ->
                Log.d("RABBITMQ", "Consumo cancelado: $tag")
            }

            // Início do consumo
            channel.basicConsume(QUEUE_NAME, true, deliverCallback, cancelCallback)
            Log.d("RABBITMQ", "Consumidor iniciado para a fila $QUEUE_NAME")
        } catch (e: Exception) {
            Log.e("RABBITMQ", "Erro ao consumir mensagens: ${e.message}")
        }
    }

    // Resultados da permissão
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, podemos prosseguir com a operação
                Toast.makeText(
                    this,
                    "Permissão para usar o Bluetooth concedida",
                    Toast.LENGTH_SHORT
                ).show()
                // Aqui, podemos incluir lógica adicional, como reconectar à impressora, se necessário
            } else {
                // Permissão negada
                Toast.makeText(this, "Permissão para usar o Bluetooth negada", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID padrão SPP
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Toast.makeText(this, "Conectado à impressora", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao conectar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun printData(data: String) {
        try {
            val enableBoldCommand = "\u001B\u0045\u0001" // ESC + E + 1 (Ativar negrito)
            outputStream?.write(enableBoldCommand.toByteArray())

            outputStream?.write(data.toByteArray())
            outputStream?.write("\n\n\n".toByteArray())


            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
