"use strict";

var username;
while ((username = prompt("Please enter your name")) == '' || username == null) { }

var pieceColors = { b: 'black', w: 'white' };
var Chat = {};
Chat.socket = null;
Chat.connect = function (host) {
    if ('WebSocket' in window) {
        Chat.socket = new WebSocket(host);
    } else if ('MozWebSocket' in window) {
        Chat.socket = new MozWebSocket(host);
    } else {
        Chat.log(JSON.stringify({ "sender": "", "message": "WebSocket is not supported by this browser" }));
        return;
    }

    Chat.socket.onopen = function () {
        Chat.log(JSON.stringify({ "sender": "", "message": "WebSocket connection opened" }));
        $(window).on('keydown', function (event) {
            if (event.which == 13) {
                Chat.sendMessage();
                return false;
            }
        });
        $('#submit-btn').click(function () {
            Chat.sendMessage();
        });
        $('#move-btn').click(function () {
            if (!hasMoved) {
                return;
            }
            var obj = {
                type: "game",
                message: { fen: game.fen(), last: Board.last }
            };
            Chat.socket.send(JSON.stringify(obj));
            $('#move-btn').html('Move Sent');
            $('#move-btn').prop('disabled', true);
            $('#msg').html('Waiting for your opponent');
            $('#undo-btn').hide();
            highlightLastMove(Board.last.color, Board.last.to, Board.last.from);
        });
        $('#undo-btn').click(function () {
            removeRedSquare();
            game.undo();
            board1.position(game.fen());
            hasMoved = false;
            if (game.in_check()) {
                redSquare(game.turn());
            }
        });
        Chat.socket.send(JSON.stringify({ type: "init", name: username }));
    };

    Chat.socket.onclose = function () {
        window.onkeydown = null;
        document.getElementById('submit-btn').onclick = null;
        document.getElementById('move-btn').onclick = null;
        Chat.log(JSON.stringify({ "sender": "", "message": "WebSocket closed" }));
    };

    Chat.socket.onmessage = function (message) {
        var obj = JSON.parse(message.data);
        if (obj.type == 'game') {
            Board.move(obj.message);
        } else {
            Chat.log(message.data);
        }
    };
};

Chat.initialize = function () {
    // GAR
    // var ipAddress = '74.105.116.178:8080';
    // FL
    // var ipAddress = '24.190.49.248:8080';
    // DEV
    var ipAddress = 'localhost:8080';
    if (window.location.protocol == 'http:') {
        Chat.connect('ws://' + ipAddress + '/user');
    } else {
        Chat.connect('wss://' + ipAddress + '/user');
    }
};

Chat.sendMessage = function () {
    var message = $(".message-input input").val();
    if ($.trim(message) != '') {
        var obj = {
            type: "chat",
            sender: username,
            message
        };
        Chat.socket.send(JSON.stringify(obj));
        $('.message-input input').val('');
    }
};

Chat.log = function (data) {
    var obj = JSON.parse(data);
    var className = (obj.sender == '') ? 'text-center' : (obj.sender == username) ? 'sent' : 'replies';

    $('<li class="' + className + '"><p>' + obj.message + '</p></li>').appendTo($('.messages ul'));
    $(".messages").animate({
        scrollTop: $(".messages").prop("scrollHeight")
    }, "fast");
};

var Board = { last: null };

Board.move = function (move) {
    var position = move.fen;
    board1.position(position);
    game.load(position);
    if (game.turn() != board1.orientation()[0]) {
        board1.orientation('flip');
    }
    removeRedSquare();
    if (move.hasOwnProperty('last')) {
        highlightLastMove(move.last.color, move.last.from, move.last.to);
    }
    if (game.game_over()) {
        if (game.in_checkmate()) {
            $('#msg').html('You lose!');
            // Highlight own king in red
            redSquare(game.turn());
        } else if (game.in_stalemate()) {
            $('#msg').html('Stalemate!');
        } else if (game.in_draw()) {
            $('#msg').html('Draw!');
        }
    } else {
        if (game.in_check()) {
            // Highlight own king in red
            redSquare(game.turn());
        }
        hasMoved = false;
        $('#msg').html('Your turn');
        $('#move-btn').html('Confirm Move');
        $('#move-btn').prop('disabled', false);
        $('#undo-btn').show();
    }
};

Chat.initialize();

var board1 = null;
var hasMoved = false;
var game = new Chess();
var whiteSquareGrey = '#a9a9a9';
var blackSquareGrey = '#696969';

function getKingSquare(color) { // Color is 'w' or 'b'
    return [...game.board()].flat()
        .map((p, idx) => (p !== null && p.type === 'k' && p.color === color) ? idx : null)
        .filter(Number.isInteger)
        .map((idx) => {
            const row = 'abcdefgh'[idx % 8]
            const column = Math.ceil((64 - idx) / 8)
            return row + column
        })[0];
}

function highlightLastMove(color, ...squares) {
    $('#board1 .square-55d63').removeClass('highlight-white');
    $('#board1 .square-55d63').removeClass('highlight-black');
    squares.forEach(square =>
        $('#board1 .square-' + square).addClass('highlight-' + color)
    );
}

function redSquare(kingColor) {
    removeRedSquare();
    var square = getKingSquare(kingColor);
    $('#board1 .square-' + square).addClass('highlight-red');
}

function removeRedSquare() {
    $('#board1 .square-55d63').removeClass('highlight-red');
}

function removeGreySquares() {
    $('#board1 .square-55d63').css('background', '');
}

function greySquare(square) {
    var $square = $('#board1 .square-' + square);
    $square.css('background', $square.hasClass('black-3c85d') ? blackSquareGrey : whiteSquareGrey);
}

function onDragStart(source, piece) {
    if (hasMoved) {
        return false;
    }
    // do not pick up pieces if the game is over
    if (game.game_over()) {
        return false;
    }

    // or if it's not that side's turn
    if ((game.turn() === 'w' && piece.search(/^b/) !== -1) ||
        (game.turn() === 'b' && piece.search(/^w/) !== -1)) {
        return false;
    }
}

function onDrop(source, target) {
    removeGreySquares()
    removeRedSquare();

    // see if the move is legal
    var move = game.move({
        from: source,
        to: target,
        promotion: 'q' // NOTE: always promote to a queen for example simplicity
    });

    // illegal move
    if (move === null) {
        return 'snapback';
    }

    Board.last = {
        color: pieceColors[move.color],
        from: source,
        to: target
    };
    hasMoved = true;
    if (move.san.includes("O-O")) {
        if (target == 'c8') {
            board1.move('a8-d8');
        } else if (target == 'g8') {
            board1.move('h8-f8');
        } else if (target == 'g1') {
            board1.move('h1-f1');
        } else {
            board1.move('a1-d1');
        }
    }
    // else if (move.hasOwnProperty("promotion")) {
    //     var pos = Chessboard.fenToObj(game.fen());
    //     pos[target] = move.color + 'Q';
    //     board1.position(pos, false);
    // }

    if (game.in_checkmate()) {
        $('#msg').html('You win!');
        // Highlight opposite king in red
        redSquare(game.turn());
        // Send game over message
        $('#move-btn').trigger('click');
        $('#msg').html('You win!');
    } else if (game.in_check()) {
        // Highlight opposite king in red
        redSquare(game.turn());
    } else if (game.in_stalemate()) {
        $('#msg').html('Stalemate!');
    } else if (game.in_draw()) {
        $('#msg').html('Draw!');
    }
}

function onMouseoverSquare(square, piece) {
    if (hasMoved) {
        return;
    }

    // get list of possible moves for this square
    var moves = game.moves({
        square: square,
        verbose: true
    });

    // exit if there are no moves available for this square
    if (moves.length === 0) {
        return;
    }

    // highlight the square they moused over
    greySquare(square);

    // highlight the possible squares for this piece
    for (var i = 0; i < moves.length; i++) {
        greySquare(moves[i].to)
    }
}

function onMouseoutSquare(square, piece) {
    removeGreySquares();
}

var config = {
    draggable: true,
    position: 'start',
    onDragStart: onDragStart,
    onDrop: onDrop,
    onMouseoutSquare: onMouseoutSquare,
    onMouseoverSquare: onMouseoverSquare,
}

board1 = Chessboard('board1', config);
window.addEventListener('resize', () => board1.resize());