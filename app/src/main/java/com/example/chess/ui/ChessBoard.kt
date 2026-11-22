package com.example.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.game.ChessGame
import com.example.chess.game.Piece
import com.example.chess.game.PieceType
import com.example.chess.game.Side
import com.example.chess.game.Square

private val BoardFrame = Color(0xFF253C31)
private val LightSquare = Color(0xFFE9DDC2)
private val DarkSquare = Color(0xFF657B60)
private val SelectedSquare = Color(0xFFD5A657)
private val RecentLightSquare = Color(0xFFDDC58E)
private val RecentDarkSquare = Color(0xFF9A9764)
private val CheckSquare = Color(0xFFAA5D4D)
private val DarkCoordinate = Color(0xFF182A20)
private val LightCoordinate = Color(0xFFFFFDF5)

@Composable
fun ChessBoard(
    game: ChessGame,
    selected: Square?,
    destinations: Set<Square>,
    onSquareClick: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastMove = game.lastMove
    val inCheck = game.isInCheck
    val frameShape = RoundedCornerShape(13.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(frameShape)
            .background(BoardFrame)
            .border(1.dp, Color(0xFF455D4D), frameShape)
            .padding(3.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
                for (row in 0..7) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0..7) {
                            val square = Square(row, col)
                            val piece = game.pieceAt(square)
                            BoardCell(
                                square = square,
                                piece = piece,
                                isSelected = square == selected,
                                isDestination = square in destinations,
                                isRecent = square == lastMove?.from || square == lastMove?.to,
                                isChecked = inCheck && piece?.type == PieceType.KING && piece.side == game.turn,
                                onClick = { onSquareClick(square) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    square: Square,
    piece: Piece?,
    isSelected: Boolean,
    isDestination: Boolean,
    isRecent: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val isLight = (square.row + square.col) % 2 == 0
    val color = when {
        isChecked -> CheckSquare
        isSelected -> SelectedSquare
        isRecent && isLight -> RecentLightSquare
        isRecent -> RecentDarkSquare
        isLight -> LightSquare
        else -> DarkSquare
    }
    val coordinateColor = if (isChecked || !(isLight || isSelected || isRecent)) {
        LightCoordinate
    } else {
        DarkCoordinate
    }
    val description = "${square.notation}, ${piece?.spokenName() ?: "пустое поле"}"
    val state = when {
        isChecked -> "Шах"
        isSelected -> "Выбрано"
        isDestination && piece != null -> "Доступно взятие"
        isDestination -> "Доступен ход"
        else -> null
    }

    Box(
        modifier = modifier
            .background(color)
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isDestination) {
            Canvas(Modifier.fillMaxSize()) {
                if (piece == null) {
                    drawCircle(
                        DarkCoordinate.copy(alpha = if (isLight) 0.65f else 1f),
                        radius = size.minDimension * 0.11f,
                    )
                } else {
                    val width = size.minDimension * 0.065f
                    drawCircle(
                        coordinateColor,
                        radius = (size.minDimension - width) * 0.5f,
                        style = Stroke(width),
                    )
                }
            }
        }
        if (piece != null) {
            ChessPiece(piece, Modifier.fillMaxSize(0.84f))
        }
        if (square.col == 0) {
            CoordinateLabel(
                text = (8 - square.row).toString(),
                color = coordinateColor,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
            )
        }
        if (square.row == 7) {
            CoordinateLabel(
                text = ('a' + square.col).toString(),
                color = coordinateColor,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun CoordinateLabel(text: String, color: Color, modifier: Modifier) {
    BasicText(
        text = text,
        modifier = modifier.clearAndSetSemantics {},
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

private fun Piece.spokenName(): String {
    val isWhite = side == Side.WHITE
    val male = if (isWhite) "белый" else "чёрный"
    val female = if (isWhite) "белая" else "чёрная"
    return when (type) {
        PieceType.KING -> "$male король"
        PieceType.QUEEN -> "$male ферзь"
        PieceType.ROOK -> "$female ладья"
        PieceType.BISHOP -> "$male слон"
        PieceType.KNIGHT -> "$male конь"
        PieceType.PAWN -> "$female пешка"
    }
}

private val PieceOutline = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val PieceRim = Stroke(width = 2.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val PieceDetail = Stroke(width = 1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun ChessPiece(piece: Piece, modifier: Modifier = Modifier) {
    val white = piece.side == Side.WHITE
    val fill = if (white) Color(0xFFFFF2D7) else Color(0xFF1E3026)
    val outline = if (white) Color(0xFF40392D) else Color(0xFF15251D)
    val rim = if (white) Color(0xFF9B7954) else Color(0xFFB9A786)
    val artwork = pieceArtwork.getValue(piece.type)

    Canvas(modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp)) {
        val unit = size.minDimension / 48f
        val side = 48f * unit
        withTransform({
            translate((size.width - side) / 2f, (size.height - side) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            if (!white) {
                drawPath(artwork.body, rim.copy(alpha = 0.65f), style = PieceRim)
                drawPath(pedestal, rim.copy(alpha = 0.65f), style = PieceRim)
            }
            drawPath(artwork.body, fill)
            drawPath(artwork.body, outline, style = PieceOutline)
            drawPath(pedestal, fill)
            drawPath(pedestal, outline, style = PieceOutline)
            drawPath(artwork.details, rim, style = PieceDetail)
            drawLine(rim, Offset(14f, 40.8f), Offset(34f, 40.8f), 0.9f, cap = StrokeCap.Round)
        }
    }
}

private class PieceArtwork(val body: Path, val details: Path)

private val pedestal: Path by lazy {
    Path().apply {
        moveTo(13f, 38f)
        lineTo(35f, 38f)
        cubicTo(37f, 38f, 38.5f, 39.6f, 38.5f, 41.6f)
        lineTo(38.5f, 43f)
        lineTo(9.5f, 43f)
        lineTo(9.5f, 41.6f)
        cubicTo(9.5f, 39.6f, 11f, 38f, 13f, 38f)
        close()
    }
}

private val pieceArtwork: Map<PieceType, PieceArtwork> by lazy {
    mapOf(
        PieceType.PAWN to PieceArtwork(
            body = Path().apply {
                moveTo(13f, 38f)
                cubicTo(13.5f, 33.8f, 20f, 31.5f, 20f, 24f)
                cubicTo(17.8f, 22.7f, 16.5f, 20.4f, 16.5f, 17.5f)
                cubicTo(16.5f, 13.4f, 19.8f, 10f, 24f, 10f)
                cubicTo(28.2f, 10f, 31.5f, 13.4f, 31.5f, 17.5f)
                cubicTo(31.5f, 20.4f, 30.2f, 22.7f, 28f, 24f)
                cubicTo(28f, 31.5f, 34.5f, 33.8f, 35f, 38f)
                close()
            },
            details = Path().apply {
                moveTo(20.5f, 25f)
                cubicTo(22.7f, 25.8f, 25.3f, 25.8f, 27.5f, 25f)
            },
        ),
        PieceType.ROOK to PieceArtwork(
            body = Path().apply {
                moveTo(12f, 38f)
                lineTo(12f, 35f)
                lineTo(16f, 31f)
                lineTo(17.5f, 23f)
                lineTo(12f, 19f)
                lineTo(12f, 9f)
                lineTo(17.5f, 9f)
                lineTo(17.5f, 14f)
                lineTo(21.1f, 14f)
                lineTo(21.1f, 9f)
                lineTo(26.9f, 9f)
                lineTo(26.9f, 14f)
                lineTo(30.5f, 14f)
                lineTo(30.5f, 9f)
                lineTo(36f, 9f)
                lineTo(36f, 19f)
                lineTo(30.5f, 23f)
                lineTo(32f, 31f)
                lineTo(36f, 35f)
                lineTo(36f, 38f)
                close()
            },
            details = Path().apply {
                moveTo(16f, 19f)
                lineTo(32f, 19f)
                moveTo(18f, 23f)
                lineTo(30f, 23f)
                moveTo(16.7f, 32f)
                lineTo(31.3f, 32f)
            },
        ),
        PieceType.KNIGHT to PieceArtwork(
            body = Path().apply {
                moveTo(14f, 38f)
                cubicTo(14.5f, 31.2f, 24f, 29.2f, 25.3f, 23.5f)
                cubicTo(23f, 25.2f, 19.6f, 24.4f, 17.5f, 26.2f)
                lineTo(14f, 29f)
                cubicTo(11.8f, 30.4f, 8.3f, 27.5f, 9f, 24.8f)
                lineTo(12.1f, 18.3f)
                lineTo(18.8f, 12.5f)
                lineTo(19.5f, 5.7f)
                lineTo(25.6f, 10.6f)
                cubicTo(33.4f, 9.7f, 39.1f, 17.1f, 39.1f, 25.5f)
                lineTo(38f, 38f)
                close()
            },
            details = Path().apply {
                moveTo(28f, 15f)
                cubicTo(34f, 17.4f, 35.4f, 24.7f, 33.5f, 33.6f)
                moveTo(17.6f, 18.8f)
                lineTo(19.7f, 17.5f)
                moveTo(12.5f, 25f)
                lineTo(13.5f, 24f)
            },
        ),
        PieceType.BISHOP to PieceArtwork(
            body = Path().apply {
                addOval(Rect(21.3f, 4.7f, 26.7f, 10.1f))
                moveTo(12.5f, 38f)
                cubicTo(13f, 34.5f, 18f, 32.7f, 20.2f, 29f)
                cubicTo(15.6f, 27.8f, 14f, 24.6f, 14.3f, 21.6f)
                cubicTo(14.7f, 16.6f, 19.4f, 12.8f, 24f, 9.5f)
                cubicTo(28.6f, 12.8f, 33.3f, 16.6f, 33.7f, 21.6f)
                cubicTo(34f, 24.6f, 32.4f, 27.8f, 27.8f, 29f)
                cubicTo(30f, 32.7f, 35f, 34.5f, 35.5f, 38f)
                close()
            },
            details = Path().apply {
                moveTo(27.7f, 15f)
                lineTo(21f, 23f)
                moveTo(19.7f, 29.1f)
                cubicTo(22.5f, 29.7f, 25.5f, 29.7f, 28.3f, 29.1f)
            },
        ),
        PieceType.QUEEN to PieceArtwork(
            body = Path().apply {
                addOval(Rect(7.5f, 7.8f, 12.9f, 13.2f))
                addOval(Rect(21.3f, 4f, 26.7f, 9.4f))
                addOval(Rect(35.1f, 7.8f, 40.5f, 13.2f))
                moveTo(12f, 38f)
                cubicTo(13f, 34.5f, 16.9f, 32.7f, 17.5f, 28.5f)
                lineTo(10f, 12.8f)
                lineTo(18.5f, 19.2f)
                lineTo(24f, 8.8f)
                lineTo(29.5f, 19.2f)
                lineTo(38f, 12.8f)
                lineTo(30.5f, 28.5f)
                cubicTo(31.1f, 32.7f, 35f, 34.5f, 36f, 38f)
                close()
            },
            details = Path().apply {
                moveTo(16.9f, 26f)
                cubicTo(21.4f, 24.5f, 26.6f, 24.5f, 31.1f, 26f)
                moveTo(18.2f, 30f)
                cubicTo(21.7f, 29f, 26.3f, 29f, 29.8f, 30f)
            },
        ),
        PieceType.KING to PieceArtwork(
            body = Path().apply {
                moveTo(22f, 3f)
                lineTo(26f, 3f)
                lineTo(26f, 6.7f)
                lineTo(30f, 6.7f)
                lineTo(30f, 10.5f)
                lineTo(26f, 10.5f)
                lineTo(26f, 14.2f)
                cubicTo(34f, 9.2f, 41f, 15.3f, 38.5f, 21.8f)
                cubicTo(37.4f, 24.7f, 34.1f, 26.1f, 31.5f, 28.6f)
                cubicTo(31.9f, 32.5f, 34.7f, 34.7f, 36f, 38f)
                lineTo(12f, 38f)
                cubicTo(13.3f, 34.7f, 16.1f, 32.5f, 16.5f, 28.6f)
                cubicTo(13.9f, 26.1f, 10.6f, 24.7f, 9.5f, 21.8f)
                cubicTo(7f, 15.3f, 14f, 9.2f, 22f, 14.2f)
                lineTo(22f, 10.5f)
                lineTo(18f, 10.5f)
                lineTo(18f, 6.7f)
                lineTo(22f, 6.7f)
                close()
            },
            details = Path().apply {
                moveTo(16.9f, 28f)
                cubicTo(21.4f, 26.2f, 26.6f, 26.2f, 31.1f, 28f)
                moveTo(24f, 15.5f)
                lineTo(24f, 24.4f)
            },
        ),
    )
}
