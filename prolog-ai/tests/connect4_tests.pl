:- use_module('../connect4').
:- use_module(library(plunit)).

stacks_board(Stacks, Board) :-
    findall(Row,
            ( between(0, 5, FromTop),
              Level is 5 - FromTop,
              findall(Cell,
                      ( member(Stack, Stacks),
                        ( nth0(Level, Stack, Found) -> Cell = Found ; Cell = 0 ) ),
                      Row) ),
            Rows),
    rows_to_board(Rows, Board).

:- begin_tests(connect4).

test(rows_to_board_stacks_pieces_from_the_bottom) :-
    Rows = [[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[1,2,0,0,0,0,0]],
    rows_to_board(Rows, Board),
    arg(1, Board, 1),
    arg(7, Board, 2),
    arg(2, Board, 0).

test(rows_to_board_rejects_wrong_shape, [fail]) :-
    rows_to_board([[0,0,0]], _).

test(rows_to_board_rejects_unknown_values, [fail]) :-
    Rows = [[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[9,0,0,0,0,0,0]],
    rows_to_board(Rows, _).

test(rows_to_board_rejects_floating_pieces, [fail]) :-
    Rows = [[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[0,0,0,0,0,0,0],[1,0,0,0,0,0,0],[0,0,0,0,0,0,0]],
    rows_to_board(Rows, _).

test(legal_moves_on_empty_board_prefers_center) :-
    stacks_board([[],[],[],[],[],[],[]], Board),
    legal_moves(Board, Moves),
    Moves == [3,2,4,1,5,0,6].

test(full_column_is_not_legal) :-
    stacks_board([[1,2,1,2,1,2],[],[],[],[],[],[]], Board),
    legal_moves(Board, Moves),
    \+ memberchk(0, Moves).

test(drop_piece_lands_on_top_of_the_stack) :-
    stacks_board([[1],[],[],[],[],[],[]], Board),
    drop_piece(Board, 0, 2, Next),
    arg(2, Next, 2),
    arg(2, Board, 0).

test(drop_piece_fails_on_a_full_column, [fail]) :-
    stacks_board([[1,2,1,2,1,2],[],[],[],[],[],[]], Board),
    drop_piece(Board, 0, 1, _).

test(detects_horizontal_four) :-
    stacks_board([[1],[1],[1],[1],[],[],[]], Board),
    four_in_a_row(Board, 1).

test(detects_vertical_four) :-
    stacks_board([[2,2,2,2],[],[],[],[],[],[]], Board),
    four_in_a_row(Board, 2).

test(detects_diagonal_four) :-
    stacks_board([[1],[2,1],[2,2,1],[2,2,2,1],[],[],[]], Board),
    four_in_a_row(Board, 1).

test(no_four_on_empty_board, [fail]) :-
    stacks_board([[],[],[],[],[],[],[]], Board),
    four_in_a_row(Board, 1).

test(takes_the_winning_move) :-
    stacks_board([[1],[1],[1],[],[2],[2],[2]], Board),
    decide(Board, 1, 4, Column, _, _, _),
    Column =:= 3.

test(blocks_the_opponent_winning_move) :-
    stacks_board([[2],[2],[2],[],[1],[],[]], Board),
    decide(Board, 1, 4, Column, _, _, _),
    Column =:= 3.

test(opens_in_the_center_on_an_empty_board) :-
    stacks_board([[],[],[],[],[],[],[]], Board),
    decide(Board, 1, 4, Column, _, _, _),
    Column =:= 3.

test(never_chooses_a_full_column) :-
    stacks_board([[1,2,1,2,1,2],[],[],[],[],[],[]], Board),
    decide(Board, 1, 3, Column, _, _, _),
    Column =\= 0.

test(reports_nodes_and_one_candidate_per_legal_move) :-
    stacks_board([[],[],[],[],[],[],[]], Board),
    decide(Board, 1, 3, _, _, Nodes, Candidates),
    Nodes > 0,
    length(Candidates, 7).

test(fails_when_the_board_is_full, [fail]) :-
    stacks_board([[1,2,1,2,1,2],[1,2,1,2,1,2],[2,1,2,1,2,1],[2,1,2,1,2,1],[1,2,1,2,1,2],[1,2,1,2,1,2],[2,1,2,1,2,1]], Board),
    decide(Board, 1, 3, _, _, _, _).

:- end_tests(connect4).
