package com.dannndi.feature.drawing

import com.dannndi.feature.common.BaseResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.random.Random

typealias ConnectedClients = MutableMap<String, DefaultWebSocketServerSession>

val gameRooms = mutableMapOf<String, GameRoom>()
var drawingPoint: DrawingPoint? = null


fun Routing.drawingWebsocket() {
    post("/game") {
        try {
            val id = uuid()
            val gameRoom = GameRoom(id = id)

            gameRooms[id] = gameRoom
            gameRooms[id]?.currentAnswer = getRandomAnimal()

            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Game Created",
                    data = gameRoom,
                )
            )
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Something Wrong!",
                    data = null,
                )
            )
        }

    }

    get("/game") {

        try {
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Game Rooms",
                    data = gameRooms.map {
                        it.value
                    }.toList(),
                )
            )
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Something Wrong!",
                    data = null,
                )
            )
        }

    }

    get("/game/{id}") {
        try {
            val id = call.parameters["id"] ?: ""
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.OK.value, message = "Game Rooms", data = gameRooms[id]
                )
            )
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Something Wrong!",
                    data = null,
                )
            )
        }

    }

    post("/game/{id}/join") {
        try {
            val id = call.parameters["id"] ?: ""
            val username = call.request.queryParameters["username"] ?: ""

            if (username.isEmpty()) {
                call.respond(
                    status = HttpStatusCode.OK, message = BaseResponse(
                        status = HttpStatusCode.Forbidden.value,
                        message = "Username cannot be empty",
                        data = null,
                    )
                )
            } else if (!gameRooms.containsKey(id)) {
                call.respond(
                    status = HttpStatusCode.OK, message = BaseResponse<GameRoom>(
                        status = HttpStatusCode.Forbidden.value,
                        message = "Room not Available for that ID",
                        data = null,
                    )
                )
            } else if (gameRooms[id]?.connectedClientsSession?.contains(username) == true) {
                call.respond(
                    status = HttpStatusCode.OK, message = BaseResponse<GameRoom>(
                        status = HttpStatusCode.Forbidden.value,
                        message = "Another username is in the Room, please change to a unique Username",
                        data = null,
                    )
                )
            } else {
                call.respond(
                    status = HttpStatusCode.OK, message = BaseResponse(
                        status = HttpStatusCode.OK.value,
                        message = "You Can Join the Game",
                        data = gameRooms[id],
                    )
                )
            }
        } catch (e: Exception) {
            call.respond(
                status = HttpStatusCode.OK, message = BaseResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Something Wrong!",
                    data = null,
                )
            )
        }
    }

    webSocket("/game/{id}") {
        try {
            val id = call.parameters["id"] ?: ""
            val username = call.request.queryParameters["username"] ?: ""
            val gameRoom = gameRooms[id]

            if (id.isEmpty() || username.isEmpty() || gameRoom == null) return@webSocket

            gameRoom.connectedClientsSession[username] = this
            gameRoom.connectedClients.add(GamePlayer(username = username))

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val decodedJson = Json.decodeFromString<IncomingMessage>(text)
                    when (decodedJson.method) {
                        "join" -> {
                            //// if game is started, there's a player that
                            if (gameRoom.isPlaying) {
                                /// just notify all client/player
                                for (client in gameRoom.connectedClientsSession) {
                                    client.value.outgoing.send(
                                        Frame.Text(
                                            "{\"method\":\"join\",\"game_room\":${
                                                Json.encodeToString(gameRoom)
                                            }}"
                                        )
                                    )
                                }
                            } else {
                                gameRoom.isPlaying = gameRoom.connectedClients.size > 1
                                if (gameRoom.currentPlayer == null) {
                                    gameRoom.currentPlayer = gameRoom.connectedClients.first()
                                }
                                ///  notify all client/player
                                for (client in gameRoom.connectedClientsSession) {
                                    client.value.outgoing.send(
                                        Frame.Text(
                                            "{\"method\":\"join\",\"game_room\":${
                                                Json.encodeToString(gameRoom)
                                            }}"
                                        )
                                    )
                                }
                            }
                        }

                        "drawing" -> {
                            when (decodedJson.type) {
                                "start" -> {
                                    decodedJson.offset?.let {
                                        drawingPoint = DrawingPoint(
                                            offsets = mutableListOf(it)
                                        )
                                        gameRoom.currentDrawingPoint?.add(drawingPoint!!)
                                        gameRoom.historyDrawingPoint = gameRoom.currentDrawingPoint?.toMutableList()
                                    }
                                }

                                "update" -> {
                                    decodedJson.offset?.let {
                                        drawingPoint = drawingPoint?.copy(offsets = drawingPoint?.offsets?.apply {
                                            add(it)
                                        } ?: mutableListOf())

                                        gameRoom.currentDrawingPoint?.removeLast()
                                        gameRoom.currentDrawingPoint?.add(drawingPoint!!)
                                        gameRoom.historyDrawingPoint = gameRoom.currentDrawingPoint?.toMutableList()
                                    }
                                }

                                "end" -> {
                                    drawingPoint = null
                                }

                                "undo" -> {
                                    if (gameRoom.currentDrawingPoint?.isNotEmpty() == true && gameRoom.historyDrawingPoint?.isNotEmpty() == true) {
                                        gameRoom.currentDrawingPoint?.removeLast()
                                    }
                                }

                                "redo" -> {
                                    if ((gameRoom.currentDrawingPoint?.size ?: 0) < (gameRoom.historyDrawingPoint?.size
                                            ?: 0)
                                    ) {
                                        val index = gameRoom.currentDrawingPoint?.size ?: 0
                                        gameRoom.currentDrawingPoint?.add(gameRoom.historyDrawingPoint!![index])
                                    }
                                }
                            }

                            for (client in gameRoom.connectedClientsSession) {
                                if (client.key == username) continue
                                client.value.outgoing.send(Frame.Text(text))
                            }
                        }

                        "answer" -> {
                            val isCorrect =
                                decodedJson.answer?.equals(gameRoom.currentAnswer, ignoreCase = true) ?: false
                            if (isCorrect) {
                                val indexPlayerThatDraw = gameRoom.connectedClients.indexOfFirst { it.username == gameRoom.currentPlayer?.username }
                                val indexPlayer = gameRoom.connectedClients.indexOfFirst { it.username == username }
                                gameRoom.connectedClients[indexPlayerThatDraw].score++
                                gameRoom.connectedClients[indexPlayer].score++
                                gameRoom.connectedClients[indexPlayer].isAnswered = true
                            }
                            val isAllAnswered = isAllAnsweredExcept(gameRoom.connectedClients, gameRoom.currentPlayer)
                            if (isAllAnswered) {
                                gameRoom.historyDrawingPoint = mutableListOf()
                                gameRoom.currentDrawingPoint = mutableListOf()

                                val indexOfCurrentPlayer =
                                    gameRoom.connectedClients.indexOfFirst { it.username == gameRoom.currentPlayer?.username }
                                val isLast = indexOfCurrentPlayer == gameRoom.connectedClients.size - 1
                                gameRoom.currentPlayer =
                                    if (isLast) gameRoom.connectedClients[0] else gameRoom.connectedClients[indexOfCurrentPlayer + 1]

                                    for ( client in gameRoom.connectedClients){
                                        client.isAnswered = false
                                    }

                                gameRoom.currentAnswer = getRandomAnimal()
                            }
                            for (client in gameRoom.connectedClientsSession) {
                                client.value.outgoing.send(
                                    Frame.Text(
                                        "{\"method\":\"answer\",\"username\":\"$username\",\"isCorrect\":$isCorrect,\"answer\":${if (isCorrect) "\"Hit ! Answer is Correct\"" else "\"${decodedJson.answer}\""},\"game_room\":${
                                            Json.encodeToString(gameRoom)
                                        }}"
                                    )
                                )
                            }
                        }
                        "ticker" ->{
                            gameRoom.currentDuration = decodedJson.duration

                            for (client in gameRoom.connectedClientsSession) {
                                client.value.outgoing.send(
                                    Frame.Text(
                                        "{\"method\":\"ticker\",\"game_room\":${
                                            Json.encodeToString(gameRoom)
                                        }}"
                                    )
                                )
                            }
                        }
                        "timeout" -> {
                            gameRoom.historyDrawingPoint = mutableListOf()
                            gameRoom.currentDrawingPoint = mutableListOf()

                            val indexOfCurrentPlayer =
                                gameRoom.connectedClients.indexOfFirst { it.username == gameRoom.currentPlayer?.username }
                            val isLast = indexOfCurrentPlayer == gameRoom.connectedClients.size - 1
                            gameRoom.currentPlayer =
                                if (isLast) gameRoom.connectedClients[0] else gameRoom.connectedClients[indexOfCurrentPlayer + 1]

                            for ( client in gameRoom.connectedClients){
                                client.isAnswered = false
                            }

                            gameRoom.currentAnswer = getRandomAnimal()

                            for (client in gameRoom.connectedClientsSession) {
                                client.value.outgoing.send(
                                    Frame.Text(
                                        "{\"method\":\"timeout\",\"game_room\":${
                                            Json.encodeToString(gameRoom)
                                        }}"
                                    )
                                )
                            }
                        }

                        "disconnect" -> {
                            gameRoom.connectedClients.removeIf { it.username == username }
                            gameRoom.connectedClientsSession.remove(username)

                            if (gameRoom.connectedClients.isEmpty()) {
                                gameRooms.remove(id)
                            } else {
                                gameRoom.isPlaying = gameRoom.connectedClients.size > 1

                                if (gameRoom.currentPlayer == null) {
                                    gameRoom.currentPlayer = gameRoom.connectedClients.first()
                                }

                                ///  notify all client/player
                                for (client in gameRoom.connectedClientsSession) {
                                    client.value.outgoing.send(
                                        Frame.Text(
                                            "{\"method\":\"disconnect\",\"game_room\":${
                                                Json.encodeToString(gameRoom)
                                            }}"
                                        )
                                    )
                                }
                            }
                        }
                    }

                }
            }
        } catch (e: Exception) {
            print("TAGGS : $e")
            val errorMessage = Json.encodeToString("{\"error_message\":${e.message ?: "Something went Wrong"}\"}")
            outgoing.send(Frame.Text(errorMessage))
        }

    }
}

fun isAllAnsweredExcept(clients: MutableList<GamePlayer>, currentPlayer: GamePlayer?): Boolean {
    for (client in clients) {
        if (client.username == currentPlayer?.username) continue
        if (!client.isAnswered) return false
    }
    return true
}

fun uuid(): String {
    return "${Random.nextInt().toString(16).substring(1)}-${
        Random.nextInt().toString(16).substring(1)
    }-${System.currentTimeMillis()}"
}

@Serializable
data class IncomingMessage(
    @SerialName("method") val method: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("answer") val answer: String? = null,
    @SerialName("offset") val offset: DrawingOffset? = null,
    @SerialName("duration") val duration: Int? = null,
)

@Serializable
data class GameRoom(
    val id: String = "-1",
    @Transient val connectedClientsSession: ConnectedClients = mutableMapOf(),
    var connectedClients: MutableList<GamePlayer> = mutableListOf(),
    var isPlaying: Boolean = false,
    var currentPlayer: GamePlayer? = null,
    var currentDuration: Int? = null,
    var currentAnswer: String? = "",
    var currentDrawingPoint: MutableList<DrawingPoint>? = mutableListOf(),
    @Transient var historyDrawingPoint: MutableList<DrawingPoint>? = mutableListOf(),
)

@Serializable
data class GamePlayer(
    var username: String = "",
    var score: Int = 0,
    var isAnswered: Boolean = false,
)


@Serializable
data class DrawingPoint(
    val offsets: MutableList<DrawingOffset> = mutableListOf(),
)

@Serializable
data class DrawingOffset(
    val dx: Double? = null,
    val dy: Double? = null,
)

fun getRandomAnimal() : String{
    val randomIndex = Random.nextInt(animalList.size)
    return  animalList[randomIndex]
}

val animalList = listOf(
    "Lion",
    "Tiger",
    "Elephant",
    "Giraffe",
    "Bear",
    "Wolf",
    "Gorilla",
    "Chimpanzee",
    "Zebra",
    "Hippopotamus",
    "Crocodile",
    "Snake",
    "Shark",
    "Whale",
    "Dolphin",
    "Seal",
    "Walrus",
    "Rhinoceros",
    "Kangaroo",
    "Platypus",
    "Echidna",
    "Tasmanian devil",
    "Koala",
    "Ostrich",
    "Emu",
    "Cassowary",
    "Alligator",
    "Lizard",
    "Iguana",
    "Gecko"
)
