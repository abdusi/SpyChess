package com.example.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.game.ChessGame
import com.example.chess.game.ChessMove
import com.example.chess.game.OutcomeReason
import com.example.chess.game.Piece
import com.example.chess.game.PieceType
import com.example.chess.game.Side
import com.example.chess.game.Square
import com.example.chess.recording.SavedRecording

private val Parchment = Color(0xFFF5EFE3)
private val Paper = Color(0xFFFFF9ED)
private val Forest = Color(0xFF172E26)
private val Moss = Color(0xFF4C6357)
private val Gold = Color(0xFFFFDFA2)

private val ChessColors = lightColorScheme(
    primary = Color(0xFF28533F),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDFEBD9),
    onSecondaryContainer = Forest,
    background = Parchment,
    onBackground = Forest,
    surface = Paper,
    onSurface = Forest,
    onSurfaceVariant = Moss,
    outline = Color(0xFF8D9A89),
)

@Composable
internal fun ChessApp(
    game: ChessGame,
    isRecording: Boolean,
    isStartingRecording: Boolean,
    audioMessage: String?,
    lastRecording: SavedRecording?,
    onPlay: (ChessMove) -> Unit,
    onClaimDraw: () -> Unit,
    onRestart: () -> Unit,
    onConfirmRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onOpenRecording: (SavedRecording) -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var promotionIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val recordingInProgress = isRecording || isStartingRecording
    var recordingControls by rememberSaveable { mutableStateOf(recordingInProgress) }
    var confirmRecording by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    val selected = selectedIndex?.let { Square(it / 8, it % 8) }
    val legalMoves = remember(game, selected) { selected?.let(game::legalMoves).orEmpty() }
    val destinations = remember(legalMoves) { legalMoves.mapTo(mutableSetOf(), ChessMove::to) }

    fun clearSelection() {
        selectedIndex = null
        promotionIndex = null
    }

    fun play(move: ChessMove) {
        clearSelection()
        onPlay(move)
    }
    val tap: (Square) -> Unit = tap@ { square ->
        val piece = game.pieceAt(square)
        if (piece?.type == PieceType.KING) {
            if (piece.side == Side.WHITE && !recordingInProgress) {
                confirmRecording = true
                return@tap
            }
            if (piece.side == Side.BLACK && recordingInProgress) {
                onSaveRecording()
                return@tap
            }
        }
        if (game.outcome != null) return@tap

        val movesToSquare = legalMoves.filter { it.to == square }
        when {
            movesToSquare.size > 1 -> promotionIndex = square.row * 8 + square.col
            movesToSquare.size == 1 -> play(movesToSquare.single())
            square == selected -> clearSelection()
            piece?.side == game.turn -> selectedIndex = square.row * 8 + square.col
            else -> clearSelection()
        }
    }

    MaterialTheme(colorScheme = ChessColors) {
        Box(
            modifier = Modifier.fillMaxSize().background(Parchment).safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Шахматы",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = Forest,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                    )
                    OutlinedButton(onClick = {
                        if (game.moveCount == 0 || game.outcome != null) {
                            clearSelection()
                            onRestart()
                        } else {
                            confirmRestart = true
                        }
                    }) { Text("Новая партия") }
                }

                if (recordingInProgress) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Gold,
                        contentColor = Forest,
                    ) {
                        Text(
                            if (isRecording) "Игра продолжается" else "Игра грузит",
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GameStatus(game)
                    ChessBoard(game, selected, destinations, tap, Modifier.fillMaxWidth())
                    if (game.claimableDraw != null) {
                        OutlinedButton(onClick = { clearSelection(); onClaimDraw() }) {
                            Text("Заявить ничью")
                        }
                    }
                }
            }
        }

        val promotionSquare = promotionIndex?.let { Square(it / 8, it % 8) }
        if (promotionSquare != null) {
            PromotionDialog(
                moves = legalMoves.filter { it.to == promotionSquare },
                side = game.turn,
                onDismiss = { promotionIndex = null },
                onChoose = ::play,
            )
        }

        if (confirmRecording) {
            AlertDialog(
                onDismissRequest = { confirmRecording = false },
                title = { Text("Начать игру?") },
                dismissButton = { TextButton(onClick = { confirmRecording = false }) { Text("Отмена") } },
                confirmButton = {
                    Button(onClick = {
                        confirmRecording = false
                        onConfirmRecording()
                    }) { Text("Yes") }
                },
            )
        }

        if (confirmRestart) {
            AlertDialog(
                onDismissRequest = { confirmRestart = false },
                title = { Text("Начать новую партию?") },
                text = { Text("Текущая позиция будет сброшена.") },
                dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Отмена") } },
                confirmButton = {
                    Button(onClick = {
                        confirmRestart = false
                        clearSelection()
                        onRestart()
                    }) { Text("Новая партия") }
                },
            )
        }
    }
}

@Composable
private fun GameStatus(game: ChessGame) {
    val outcome = game.outcome
    val title = when {
        outcome?.winner == Side.WHITE -> "Белые победили"
        outcome?.winner == Side.BLACK -> "Чёрные победили"
        outcome != null -> "Ничья"
        game.turn == Side.WHITE -> "Ход белых"
        else -> "Ход чёрных"
    }
    val detail = when (outcome?.reason) {
        OutcomeReason.CHECKMATE -> "Мат"
        OutcomeReason.STALEMATE -> "Пат"
        OutcomeReason.INSUFFICIENT_MATERIAL -> "Недостаточно фигур для мата"
        OutcomeReason.THREEFOLD_REPETITION -> "Троекратное повторение позиции"
        OutcomeReason.FIVEFOLD_REPETITION -> "Пятикратное повторение позиции"
        OutcomeReason.FIFTY_MOVE_RULE -> "Правило 50 ходов"
        OutcomeReason.SEVENTY_FIVE_MOVE_RULE -> "Правило 75 ходов"
        null -> if (game.isInCheck) "Шах" else "Ход ${game.moveCount / 2 + 1}"
    }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Forest, contentColor = Paper) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(Color(0xFFB7C4A9), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ChessPiece(Piece(outcome?.winner ?: game.turn, PieceType.KING), Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = Gold)
            }
        }
    }
}

@Composable
private fun RecordingControls(
    enabled: Boolean,
    message: String?,
    lastRecording: SavedRecording?,
    onToggle: () -> Unit,
    onOpenRecording: (SavedRecording) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else Paper,
        contentColor = Forest,
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onToggle) {
                    Text(if (enabled) "К игре" else "Управление")
                }
            }
            if (enabled) {
                Text("Белый король - вы", style = MaterialTheme.typography.bodyMedium)
                Text("Чёрный король — остановка и сохранение", style = MaterialTheme.typography.bodyMedium)
            }
            if (message != null) {
                Text(
                    message,
                    modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Moss,
                )
            }
            if (lastRecording != null) {
                TextButton(onClick = { onOpenRecording(lastRecording) }) { Text("Открыть запись") }
            }
        }
    }
}

@Composable
private fun PromotionDialog(
    moves: List<ChessMove>,
    side: Side,
    onDismiss: () -> Unit,
    onChoose: (ChessMove) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Превращение пешки") },
        text = {
            Column {
                for (move in moves) {
                    val type = requireNotNull(move.promotion)
                    TextButton(onClick = { onChoose(move) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChessPiece(Piece(side, type), Modifier.size(28.dp))
                            Text(when (type) {
                                PieceType.QUEEN -> "Ферзь"
                                PieceType.ROOK -> "Ладья"
                                PieceType.BISHOP -> "Слон"
                                PieceType.KNIGHT -> "Конь"
                                PieceType.KING, PieceType.PAWN -> error("Invalid promotion: $type")
                            })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
