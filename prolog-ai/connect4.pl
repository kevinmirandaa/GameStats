:- module(connect4, [
    rows_to_board/2,
    legal_moves/2,
    drop_piece/4,
    four_in_a_row/2,
    decide/7
]).

:- dynamic all_windows/1.

column_count(7).
row_count(6).
cell_total(42).
win_score(1000000).
infinity(100000000).
column_weights([3, 4, 5, 7, 5, 4, 3]).

opponent(1, 2).
opponent(2, 1).

cell_index(Column, Row, Index) :-
    row_count(Rows),
    Index is Column * Rows + Row + 1.

build_windows :-
    retractall(all_windows(_)),
    column_count(Columns),
    row_count(Rows),
    LastColumn is Columns - 1,
    LastRow is Rows - 1,
    findall(Indexes,
            ( between(0, LastColumn, StartColumn),
              between(0, LastRow, StartRow),
              member(DeltaColumn-DeltaRow, [1-0, 0-1, 1-1, 1-(-1)]),
              EndColumn is StartColumn + 3 * DeltaColumn,
              EndRow is StartRow + 3 * DeltaRow,
              between(0, LastColumn, EndColumn),
              between(0, LastRow, EndRow),
              findall(Index,
                      ( between(0, 3, Step),
                        Column is StartColumn + Step * DeltaColumn,
                        Row is StartRow + Step * DeltaRow,
                        cell_index(Column, Row, Index) ),
                      Indexes) ),
            Windows),
    assertz(all_windows(Windows)).

:- build_windows.

rows_to_board(Rows, Board) :-
    row_count(RowCount),
    column_count(ColumnCount),
    length(Rows, RowCount),
    maplist(row_is_valid(ColumnCount), Rows),
    reverse(Rows, BottomUp),
    cell_total(Total),
    functor(Board, c, Total),
    LastColumn is ColumnCount - 1,
    LastRow is RowCount - 1,
    forall(
        ( between(0, LastColumn, Column),
          between(0, LastRow, Row) ),
        ( nth0(Row, BottomUp, RowCells),
          nth0(Column, RowCells, Cell),
          cell_index(Column, Row, Index),
          nb_setarg(Index, Board, Cell) )
    ),
    columns_are_stacked(Board).

row_is_valid(Width, Row) :-
    length(Row, Width),
    maplist(valid_cell, Row).

valid_cell(Cell) :- integer(Cell), memberchk(Cell, [0, 1, 2]).

columns_are_stacked(Board) :-
    column_count(Columns),
    row_count(Rows),
    LastColumn is Columns - 1,
    LastRow is Rows - 1,
    forall(
        ( between(0, LastColumn, Column),
          between(1, LastRow, Row) ),
        ( cell_index(Column, Row, Above),
          Below is Above - 1,
          arg(Above, Board, AboveCell),
          arg(Below, Board, BelowCell),
          (   AboveCell =:= 0 -> true ; BelowCell =\= 0 ) )
    ).

legal_moves(Board, Moves) :-
    row_count(Rows),
    findall(Column,
            ( member(Column, [3, 2, 4, 1, 5, 0, 6]),
              TopIndex is Column * Rows + Rows,
              arg(TopIndex, Board, 0) ),
            Moves).

drop_piece(Board, Column, Piece, NewBoard) :-
    row_count(Rows),
    Base is Column * Rows,
    lowest_free(Board, Base, 1, Rows, Index),
    duplicate_term(Board, NewBoard),
    nb_setarg(Index, NewBoard, Piece).

lowest_free(Board, Base, Row, LastRow, Index) :-
    Row =< LastRow,
    Candidate is Base + Row,
    arg(Candidate, Board, Cell),
    (   Cell =:= 0
    ->  Index = Candidate
    ;   NextRow is Row + 1,
        lowest_free(Board, Base, NextRow, LastRow, Index)
    ).

four_in_a_row(Board, Piece) :-
    integer(Piece),
    Piece > 0,
    all_windows(Windows),
    member([A, B, C, D], Windows),
    arg(A, Board, Piece),
    arg(B, Board, Piece),
    arg(C, Board, Piece),
    arg(D, Board, Piece),
    !.

count_pieces([], _, _, Mine, Theirs, Mine, Theirs).
count_pieces([Index | Rest], Board, Piece, MineSoFar, TheirsSoFar, Mine, Theirs) :-
    arg(Index, Board, Cell),
    (   Cell =:= 0
    ->  NextMine = MineSoFar, NextTheirs = TheirsSoFar
    ;   Cell =:= Piece
    ->  NextMine is MineSoFar + 1, NextTheirs = TheirsSoFar
    ;   NextMine = MineSoFar, NextTheirs is TheirsSoFar + 1
    ),
    count_pieces(Rest, Board, Piece, NextMine, NextTheirs, Mine, Theirs).

window_score(Mine, Theirs, Score) :-
    (   Theirs =:= 0, Mine =:= 3 -> Score = 50
    ;   Theirs =:= 0, Mine =:= 2 -> Score = 10
    ;   Mine =:= 0, Theirs =:= 3 -> Score = -60
    ;   Mine =:= 0, Theirs =:= 2 -> Score = -8
    ;   Score = 0
    ).

sum_window_scores([], _, _, Total, Total).
sum_window_scores([Window | Rest], Board, Piece, TotalSoFar, Total) :-
    count_pieces(Window, Board, Piece, 0, 0, Mine, Theirs),
    window_score(Mine, Theirs, Score),
    NextTotal is TotalSoFar + Score,
    sum_window_scores(Rest, Board, Piece, NextTotal, Total).

positional_score(Board, Piece, Score) :-
    column_weights(Weights),
    positional_from(1, Board, Piece, Weights, 0, Score).

positional_from(Index, _, _, _, Score, Score) :-
    cell_total(Total),
    Index > Total,
    !.
positional_from(Index, Board, Piece, Weights, ScoreSoFar, Score) :-
    arg(Index, Board, Cell),
    row_count(Rows),
    Column is (Index - 1) // Rows,
    nth0(Column, Weights, Weight),
    (   Cell =:= 0 -> NextScore = ScoreSoFar
    ;   Cell =:= Piece -> NextScore is ScoreSoFar + Weight
    ;   NextScore is ScoreSoFar - Weight
    ),
    NextIndex is Index + 1,
    positional_from(NextIndex, Board, Piece, Weights, NextScore, Score).

evaluate(Board, Piece, Score) :-
    all_windows(Windows),
    sum_window_scores(Windows, Board, Piece, 0, WindowTotal),
    positional_score(Board, Piece, PositionalTotal),
    Score is WindowTotal + PositionalTotal.

count_node :-
    nb_getval(search_nodes, Nodes),
    Next is Nodes + 1,
    nb_setval(search_nodes, Next).

negamax(Board, Player, Depth, Alpha, Beta, Score) :-
    opponent(Player, Opponent),
    win_score(WinScore),
    (   four_in_a_row(Board, Opponent)
    ->  Score is -(WinScore + Depth)
    ;   legal_moves(Board, Moves),
        (   Moves == []
        ->  Score = 0
        ;   Depth =:= 0
        ->  evaluate(Board, Player, Score)
        ;   infinity(Infinity),
            Floor is -Infinity,
            search_moves(Moves, Board, Player, Depth, Alpha, Beta, Floor, Score)
        )
    ).

search_moves([], _, _, _, _, _, Best, Best).
search_moves([Column | Rest], Board, Player, Depth, Alpha, Beta, BestSoFar, Best) :-
    drop_piece(Board, Column, Player, Next),
    opponent(Player, Opponent),
    count_node,
    NextDepth is Depth - 1,
    NextAlpha is -Beta,
    NextBeta is -Alpha,
    negamax(Next, Opponent, NextDepth, NextAlpha, NextBeta, Negated),
    MoveScore is -Negated,
    NewBest is max(BestSoFar, MoveScore),
    NewAlpha is max(Alpha, NewBest),
    (   NewAlpha >= Beta
    ->  Best = NewBest
    ;   search_moves(Rest, Board, Player, Depth, NewAlpha, Beta, NewBest, Best)
    ).

decide(Board, Player, Depth, Column, Score, Nodes, Candidates) :-
    nb_setval(search_nodes, 0),
    legal_moves(Board, Moves),
    Moves \== [],
    opponent(Player, Opponent),
    infinity(Infinity),
    NegativeInfinity is -Infinity,
    RootDepth is Depth - 1,
    findall(Move-MoveScore,
            ( member(Move, Moves),
              drop_piece(Board, Move, Player, Next),
              count_node,
              negamax(Next, Opponent, RootDepth, NegativeInfinity, Infinity, Negated),
              MoveScore is -Negated ),
            Candidates),
    best_candidate(Candidates, Column, Score),
    nb_getval(search_nodes, Nodes).

best_candidate([First | Rest], Column, Score) :-
    foldl(keep_better, Rest, First, Column-Score).

keep_better(Move-MoveScore, _-BestScore, Move-MoveScore) :- MoveScore > BestScore, !.
keep_better(_, Best, Best).
