package com.example.chess.game

enum class Side {
    WHITE, BLACK;

    val opponent: Side
        get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

data class Square(val row: Int, val col: Int) {
    val notation: String
        get() = "${'a' + col}${8 - row}"
}

data class Piece(val side: Side, val type: PieceType)

data class ChessMove(val from: Square, val to: Square, val promotion: PieceType? = null)

enum class OutcomeReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIVEFOLD_REPETITION,
    SEVENTY_FIVE_MOVE_RULE,
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
}

data class GameOutcome(val winner: Side?, val reason: OutcomeReason)

class ChessGame private constructor(
    private val position: Position,
    private val repetitions: Map<String, Int>,
    private val repetitionCount: Int,
    val moveCount: Int,
    val lastMove: ChessMove?,
    private val claimedDraw: OutcomeReason? = null,
) {
    val turn: Side get() = position.turn
    val isInCheck: Boolean by lazy { position.inCheck(turn) }

    val outcome: GameOutcome? by lazy {
        when {
            claimedDraw != null -> GameOutcome(null, claimedDraw)
            !position.hasLegalMove() -> if (isInCheck) {
                GameOutcome(turn.opponent, OutcomeReason.CHECKMATE)
            } else {
                GameOutcome(null, OutcomeReason.STALEMATE)
            }
            position.hasInsufficientMaterial() -> GameOutcome(null, OutcomeReason.INSUFFICIENT_MATERIAL)
            repetitionCount >= 5 -> GameOutcome(null, OutcomeReason.FIVEFOLD_REPETITION)
            position.halfMoveClock >= 150 -> GameOutcome(null, OutcomeReason.SEVENTY_FIVE_MOVE_RULE)
            else -> null
        }
    }

    val claimableDraw: OutcomeReason?
        get() = when {
            outcome != null -> null
            repetitionCount >= 3 -> OutcomeReason.THREEFOLD_REPETITION
            position.halfMoveClock >= 100 -> OutcomeReason.FIFTY_MOVE_RULE
            else -> null
        }

    fun pieceAt(square: Square): Piece? = position.pieceAt(square)

    fun legalMoves(from: Square): List<ChessMove> =
        if (outcome == null) position.legalMoves(from) else emptyList()

    fun play(move: ChessMove): ChessGame {
        require(move in legalMoves(move.from)) { "Illegal move: $move" }
        val next = position.after(move)
        val key = next.repetitionKey()
        val count = (repetitions[key] ?: 0) + 1
        return ChessGame(next, repetitions + (key to count), count, moveCount + 1, move)
    }

    fun claimDraw(): ChessGame {
        val reason = requireNotNull(claimableDraw) { "No draw is available to claim" }
        return ChessGame(position, repetitions, repetitionCount, moveCount, lastMove, reason)
    }

    companion object {
        fun initial(): ChessGame {
            val position = Position.initial()
            return ChessGame(position, mapOf(position.repetitionKey() to 1), 1, 0, null)
        }
    }
}

private val Square.index: Int get() = row * 8 + col
private val Square.isOnBoard: Boolean get() = row in 0..7 && col in 0..7
private val Side.homeRow: Int get() = if (this == Side.WHITE) 7 else 0
private val Side.pawnDirection: Int get() = if (this == Side.WHITE) -1 else 1

private fun Square.offset(rows: Int, cols: Int) = Square(row + rows, col + cols)

private val queenDirections = listOf(
    -1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1,
)
private val rookDirections = queenDirections.take(4)
private val bishopDirections = queenDirections.drop(4)
private val knightDirections = listOf(
    -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1,
)
private val promotionTypes = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
private val boardSquares = List(64) { index -> Square(index / 8, index % 8) }

private data class Position(
    val board: List<Piece?>,
    val turn: Side,
    val castlingRooks: Set<Square>,
    val enPassant: Square? = null,
    val halfMoveClock: Int = 0,
) {
    fun pieceAt(square: Square): Piece? = if (square.isOnBoard) board[square.index] else null

    fun legalMoves(from: Square): List<ChessMove> {
        val piece = pieceAt(from) ?: return emptyList()
        if (piece.side != turn) return emptyList()
        return candidateMoves(from, piece).filter { move -> !after(move).inCheck(turn) }
    }

    fun hasLegalMove(): Boolean = boardSquares.any { legalMoves(it).isNotEmpty() }

    private fun candidateMoves(from: Square, piece: Piece): List<ChessMove> = when (piece.type) {
        PieceType.PAWN -> pawnMoves(from, piece.side)
        PieceType.KNIGHT -> stepMoves(from, knightDirections, piece.side)
        PieceType.BISHOP -> rayMoves(from, bishopDirections, piece.side)
        PieceType.ROOK -> rayMoves(from, rookDirections, piece.side)
        PieceType.QUEEN -> rayMoves(from, queenDirections, piece.side)
        PieceType.KING -> stepMoves(from, queenDirections, piece.side) + castleMoves(from, piece.side)
    }

    private fun pawnMoves(from: Square, side: Side): List<ChessMove> = buildList {
        fun addPawnMove(to: Square) {
            if (to.row == side.opponent.homeRow) {
                promotionTypes.forEach { add(ChessMove(from, to, it)) }
            } else {
                add(ChessMove(from, to))
            }
        }

        val oneStep = from.offset(side.pawnDirection, 0)
        if (oneStep.isOnBoard && pieceAt(oneStep) == null) {
            addPawnMove(oneStep)
            val twoSteps = oneStep.offset(side.pawnDirection, 0)
            if (from.row == side.homeRow + side.pawnDirection && pieceAt(twoSteps) == null) {
                add(ChessMove(from, twoSteps))
            }
        }
        for (colDelta in listOf(-1, 1)) {
            val target = from.offset(side.pawnDirection, colDelta)
            if (target == enPassant || pieceAt(target).canBeCapturedBy(side)) {
                addPawnMove(target)
            }
        }
    }

    private fun stepMoves(from: Square, directions: List<Pair<Int, Int>>, side: Side): List<ChessMove> =
        directions.mapNotNull { (rows, cols) ->
            val target = from.offset(rows, cols)
            val occupant = pieceAt(target)
            if (target.isOnBoard && (occupant == null || occupant.canBeCapturedBy(side))) {
                ChessMove(from, target)
            } else {
                null
            }
        }

    private fun rayMoves(from: Square, directions: List<Pair<Int, Int>>, side: Side): List<ChessMove> =
        buildList {
            for ((rows, cols) in directions) {
                var target = from.offset(rows, cols)
                while (target.isOnBoard) {
                    val occupant = pieceAt(target)
                    if (occupant == null || occupant.canBeCapturedBy(side)) {
                        add(ChessMove(from, target))
                    }
                    if (occupant != null) break
                    target = target.offset(rows, cols)
                }
            }
        }

    private fun castleMoves(from: Square, side: Side): List<ChessMove> = buildList {
        val row = side.homeRow
        if (from != Square(row, 4) || isAttacked(from, side.opponent)) return@buildList

        for (rookCol in listOf(0, 7)) {
            val rookSquare = Square(row, rookCol)
            if (rookSquare !in castlingRooks || pieceAt(rookSquare) != Piece(side, PieceType.ROOK)) continue

            val direction = if (rookCol == 0) -1 else 1
            val clearSquares = if (rookCol == 0) 1..3 else 5..6
            val transit = from.offset(0, direction)
            if (clearSquares.all { pieceAt(Square(row, it)) == null } &&
                !after(ChessMove(from, transit)).inCheck(side)
            ) {
                add(ChessMove(from, from.offset(0, direction * 2)))
            }
        }
    }

    fun after(move: ChessMove): Position {
        val piece = checkNotNull(pieceAt(move.from))
        val nextBoard = board.toMutableList()
        val captureSquare = if (piece.type == PieceType.PAWN && move.to == enPassant) {
            Square(move.from.row, move.to.col)
        } else {
            move.to
        }
        val captured = pieceAt(captureSquare)
        nextBoard[captureSquare.index] = null
        nextBoard[move.from.index] = null
        nextBoard[move.to.index] = move.promotion?.let { Piece(piece.side, it) } ?: piece

        val colDelta = move.to.col - move.from.col
        if (piece.type == PieceType.KING && (colDelta == 2 || colDelta == -2)) {
            val rookFrom = Square(move.from.row, if (colDelta == 2) 7 else 0)
            val rookTo = Square(move.from.row, (move.from.col + move.to.col) / 2)
            nextBoard[rookTo.index] = nextBoard[rookFrom.index]
            nextBoard[rookFrom.index] = null
        }

        var nextCastlingRooks = castlingRooks
        if (piece.type == PieceType.KING) {
            nextCastlingRooks = nextCastlingRooks - Square(piece.side.homeRow, 0) - Square(piece.side.homeRow, 7)
        } else if (piece.type == PieceType.ROOK) {
            nextCastlingRooks = nextCastlingRooks - move.from
        }
        if (captured?.type == PieceType.ROOK) nextCastlingRooks = nextCastlingRooks - captureSquare

        val pawnMoved = piece.type == PieceType.PAWN
        val nextEnPassant = if (pawnMoved && move.to.row - move.from.row == 2 * piece.side.pawnDirection) {
            move.from.offset(piece.side.pawnDirection, 0)
        } else {
            null
        }
        return Position(
            board = nextBoard,
            turn = turn.opponent,
            castlingRooks = nextCastlingRooks,
            enPassant = nextEnPassant,
            halfMoveClock = if (pawnMoved || captured != null) 0 else halfMoveClock + 1,
        )
    }

    fun inCheck(side: Side): Boolean {
        val king = boardSquares[board.indexOf(Piece(side, PieceType.KING))]
        return isAttacked(king, side.opponent)
    }

    private fun isAttacked(square: Square, attacker: Side): Boolean {
        val pawn = Piece(attacker, PieceType.PAWN)
        val pawnRow = -attacker.pawnDirection
        if (pieceAt(square.offset(pawnRow, -1)) == pawn || pieceAt(square.offset(pawnRow, 1)) == pawn) return true

        val knight = Piece(attacker, PieceType.KNIGHT)
        if (knightDirections.any { (rows, cols) -> pieceAt(square.offset(rows, cols)) == knight }) return true

        val king = Piece(attacker, PieceType.KING)
        if (queenDirections.any { (rows, cols) -> pieceAt(square.offset(rows, cols)) == king }) return true

        for ((rows, cols) in queenDirections) {
            val slider = if (rows == 0 || cols == 0) PieceType.ROOK else PieceType.BISHOP
            var target = square.offset(rows, cols)
            while (target.isOnBoard) {
                val piece = pieceAt(target)
                if (piece != null) {
                    if (piece.side == attacker && (piece.type == slider || piece.type == PieceType.QUEEN)) return true
                    break
                }
                target = target.offset(rows, cols)
            }
        }
        return false
    }

    fun hasInsufficientMaterial(): Boolean {
        val otherPieces = boardSquares.filter { square ->
            pieceAt(square)?.type?.let { it != PieceType.KING } == true
        }
        if (otherPieces.isEmpty()) return true
        if (otherPieces.size == 1 && pieceAt(otherPieces.first())?.type == PieceType.KNIGHT) return true
        val firstColor = (otherPieces.first().row + otherPieces.first().col) % 2
        return otherPieces.all { square ->
            pieceAt(square)?.type == PieceType.BISHOP && (square.row + square.col) % 2 == firstColor
        }
    }

    fun repetitionKey(): String = buildString {
        for (piece in board) {
            val symbol = when (piece?.type) {
                PieceType.KING -> 'K'
                PieceType.QUEEN -> 'Q'
                PieceType.ROOK -> 'R'
                PieceType.BISHOP -> 'B'
                PieceType.KNIGHT -> 'N'
                PieceType.PAWN -> 'P'
                null -> '.'
            }
            append(if (piece?.side == Side.BLACK) symbol.lowercaseChar() else symbol)
        }
        append(if (turn == Side.WHITE) 'w' else 'b')
        for (square in initialCastlingRooks) append(if (square in castlingRooks) '1' else '0')
        enPassant?.let { target ->
            // Repetition distinguishes an en passant target only if a capture is legal.
            if (listOf(-1, 1).any { colDelta ->
                    val from = target.offset(-turn.pawnDirection, colDelta)
                    pieceAt(from) == Piece(turn, PieceType.PAWN) && legalMoves(from).any { it.to == target }
                }
            ) {
                append(target.notation)
            }
        }
    }

    companion object {
        private val initialCastlingRooks = setOf(Square(0, 0), Square(0, 7), Square(7, 0), Square(7, 7))

        fun initial(): Position {
            val backRank = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK,
            )
            val board = boardSquares.map { square ->
                when (square.row) {
                    0 -> Piece(Side.BLACK, backRank[square.col])
                    1 -> Piece(Side.BLACK, PieceType.PAWN)
                    6 -> Piece(Side.WHITE, PieceType.PAWN)
                    7 -> Piece(Side.WHITE, backRank[square.col])
                    else -> null
                }
            }
            return Position(board, Side.WHITE, initialCastlingRooks)
        }
    }
}

private fun Piece?.canBeCapturedBy(side: Side): Boolean =
    this != null && this.side != side && type != PieceType.KING
