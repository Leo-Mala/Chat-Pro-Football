from __future__ import annotations
from typing import Any

PREMIER_LEAGUE_2026_27 = [
    ("Arsenal FC", "London"),
    ("Aston Villa", "Birmingham"),
    ("AFC Bournemouth", "Bournemouth"),
    ("Brentford FC", "London"),
    ("Brighton & Hove Albion", "Brighton"),
    ("Chelsea FC", "London"),
    ("Coventry City", "Coventry"),
    ("Crystal Palace", "London"),
    ("Everton FC", "Liverpool"),
    ("Fulham FC", "London"),
    ("Hull City", "Hull"),
    ("Ipswich Town", "Ipswich"),
    ("Leeds United", "Leeds"),
    ("Liverpool FC", "Liverpool"),
    ("Manchester City", "Manchester"),
    ("Manchester United", "Manchester"),
    ("Newcastle United", "Newcastle"),
    ("Nottingham Forest", "Nottingham"),
    ("Sunderland AFC", "Sunderland"),
    ("Tottenham Hotspur", "London"),
]

# 18 active players: 2 GK, 6 defenders, 5 midfielders, 5 attackers.
POSITION_TEMPLATE = [
    "Goalkeeper", "Goalkeeper",
    "Defender", "Defender", "Defender", "Defender", "Left-Back", "Right-Back",
    "Midfielder", "Midfielder", "Midfielder", "Defensive Midfield", "Defensive Midfield",
    "Forward", "Forward", "Forward", "Forward", "Forward",
]

def build_premier_league_api_fixture() -> dict[str, Any]:
    teams = []
    players = []
    provider_team_ids: dict[str, int] = {}

    for club_index, (club_name, city) in enumerate(PREMIER_LEAGUE_2026_27, start=1):
        provider_team_id = 9000 + club_index
        provider_team_ids[club_name] = provider_team_id
        teams.append({
            "team": {
                "id": provider_team_id,
                "name": club_name,
                "logo": "https://invalid.fixture/logo.png",
            },
            "venue": {
                "name": f"Fixture Stadium {club_index:02d}",
                "city": city,
                "image": "https://invalid.fixture/stadium.png",
            },
        })

        for player_index, position in enumerate(POSITION_TEMPLATE, start=1):
            provider_player_id = 100000 + club_index * 100 + player_index
            players.append({
                "player": {
                    "id": provider_player_id,
                    "name": f"F{club_index:02d} P{player_index:02d}",
                    "birth": {"date": f"{1990 + (player_index % 10):04d}-{((player_index - 1) % 12) + 1:02d}-01"},
                    "nationality": "Fixture",
                    "photo": "https://invalid.fixture/player.png",
                },
                "statistics": [{
                    "team": {"id": provider_team_id, "logo": "https://invalid.fixture/logo.png"},
                    "games": {
                        "position": position,
                        "number": player_index,
                        "rating": "9.9",
                    },
                }],
            })

    # One synthetic loan identity is present in the borrower raw squad and then removed by
    # normalization, proving it is materialized exactly once through PlayerLoan.
    loan_provider_player_id = 199999
    borrower = provider_team_ids["Manchester United"]
    owner = provider_team_ids["Arsenal FC"]
    players.append({
        "player": {
            "id": loan_provider_player_id,
            "name": "Fixture Loan Player",
            "birth": {"date": "2001-06-15"},
            "nationality": "Fixture",
            "photo": "https://invalid.fixture/player.png",
        },
        "statistics": [{
            "team": {"id": borrower},
            "games": {"position": "Midfielder", "number": 99, "rating": "8.8"},
        }],
    })
    transfers = [{
        "player": {"id": loan_provider_player_id, "name": "Fixture Loan Player"},
        "transfers": [{
            "date": "2026-08-18",
            "type": "Loan",
            "teams": {
                "out": {"id": owner, "name": "Arsenal FC", "logo": "https://invalid.fixture/logo.png"},
                "in": {"id": borrower, "name": "Manchester United", "logo": "https://invalid.fixture/logo.png"},
            },
        }],
    }]

    return {
        "provider": "fixture",
        "teamsResponse": {"response": teams},
        "playersResponse": {"response": players},
        "transfersResponse": {"response": transfers},
    }
