:- use_module(library(http/thread_httpd)).
:- use_module(library(http/http_dispatch)).
:- use_module(library(http/http_json)).
:- use_module(connect4).

:- http_handler(root(decide), handle_decide, [method(post)]).
:- http_handler(root(health), handle_health, [method(get)]).

default_port(8090).
default_depth(5).
maximum_depth(8).

handle_health(_Request) :-
    reply_json_dict(_{status: "UP"}).

handle_decide(Request) :-
    catch(
        ( http_read_json_dict(Request, Body),
          answer_decision(Body, Answer),
          reply_json_dict(Answer)
        ),
        Problem,
        reply_problem(Problem)
    ).

answer_decision(Body, Answer) :-
    (   get_dict(board, Body, Rows) -> true ; throw(invalid_request("board is required")) ),
    (   get_dict(player, Body, Player), memberchk(Player, [1, 2]) -> true ; throw(invalid_request("player must be 1 or 2")) ),
    default_depth(DefaultDepth),
    maximum_depth(MaximumDepth),
    (   get_dict(depth, Body, Requested), integer(Requested) -> Depth is max(1, min(MaximumDepth, Requested)) ; Depth = DefaultDepth ),
    (   catch(rows_to_board(Rows, Columns), _, fail) -> true ; throw(invalid_request("board must be 6 rows of 7 cells with values 0, 1 or 2")) ),
    (   decide(Columns, Player, Depth, Column, Score, Nodes, Candidates) -> true ; throw(invalid_request("board has no legal moves")) ),
    maplist(candidate_dict, Candidates, CandidateDicts),
    Answer = _{column: Column, score: Score, nodes: Nodes, depth: Depth, candidates: CandidateDicts}.

candidate_dict(Column-Score, _{column: Column, score: Score}).

reply_problem(invalid_request(Message)) :-
    !,
    reply_json_dict(_{error: Message}, [status(400)]).
reply_problem(Other) :-
    format(string(Message), "~w", [Other]),
    reply_json_dict(_{error: Message}, [status(400)]).

main :-
    default_port(DefaultPort),
    (   getenv('PORT', Text), atom_number(Text, Port) -> true ; Port = DefaultPort ),
    http_server(http_dispatch, [port(Port)]),
    format(user_error, "prolog-ai listening on ~w~n", [Port]),
    thread_get_message(never_arrives).

:- initialization(main, main).
