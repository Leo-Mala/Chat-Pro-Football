package com.example.data

/**
 * Manchester United — snapshot factual em 2026-08-18.
 *
 * Source of truth: página oficial atual do Men's Team e perfis individuais do Manchester United.
 * A janela inglesa de verão de 2026 segue aberta até 2026-09-01, portanto este arquivo é um
 * snapshot datado e NÃO é declarado como elenco final da temporada.
 *
 * Andre Onana aparece explicitamente como "On Loan" na página/perfil oficial em 2026-08-18 e,
 * por isso, não é materializado como membro ativo deste snapshot. Altay Bayindir aparece como
 * goleiro ativo e permanece no elenco. O vínculo de propriedade/empréstimo de Onana será tratado
 * pela futura camada factual de loans.
 *
 * Mapeamento de posição para o modelo do jogo:
 * - categoria oficial Forward -> ATA;
 * - categoria oficial Midfielder -> MEI, exceto Ugarte -> VOL pela função defensiva descrita no
 *   próprio perfil do clube;
 * - laterais/full-backs -> LAT;
 * - centre-backs -> ZAG;
 * - Goalkeeper -> GOL.
 */
object ManchesterUnitedSquad2026_27 {
    const val VERIFIED_AS_OF = "2026-08-18"

    val snapshot = EuropeanRealSquadSnapshot(
        country = "Inglaterra",
        clubName = "Manchester United",
        domesticSeasonLabel = "2026/27",
        verifiedAsOfIso = VERIFIED_AS_OF,
        sourceRefs = listOf(
            "https://www.manutd.com/en/teams/mens-team",
            "https://www.manutd.com/en/teams/mens-team/matheus-cunha",
            "https://www.manutd.com/en/teams/mens-team/amad-diallo",
            "https://www.manutd.com/en/teams/mens-team/shea-lacey",
            "https://www.manutd.com/en/teams/mens-team/bryan-mbeumo",
            "https://www.manutd.com/en/teams/mens-team/marcus-rashford",
            "https://www.manutd.com/en/teams/mens-team/benjamin-sesko",
            "https://www.manutd.com/en/teams/mens-team/joshua-zirkzee",
            "https://www.manutd.com/en/teams/mens-team/toby-collyer",
            "https://www.manutd.com/en/teams/mens-team/bruno-fernandes",
            "https://www.manutd.com/en/teams/mens-team/jack-fletcher",
            "https://www.manutd.com/en/teams/mens-team/tyler-fletcher",
            "https://www.manutd.com/en/teams/mens-team/kobbie-mainoo",
            "https://www.manutd.com/en/teams/mens-team/mason-mount",
            "https://www.manutd.com/en/teams/mens-team/andrey-santos",
            "https://www.manutd.com/en/teams/mens-team/youri-tielemans",
            "https://www.manutd.com/en/teams/mens-team/manuel-ugarte",
            "https://www.manutd.com/en/teams/mens-team/harry-amass",
            "https://www.manutd.com/en/teams/mens-team/patrick-dorgu",
            "https://www.manutd.com/en/teams/mens-team/diogo-dalot",
            "https://www.manutd.com/en/teams/mens-team/matthijs-de-ligt",
            "https://www.manutd.com/en/teams/mens-team/tyler-fredricson",
            "https://www.manutd.com/en/teams/mens-team/ayden-heaven",
            "https://www.manutd.com/en/teams/mens-team/harry-maguire",
            "https://www.manutd.com/en/teams/mens-team/lisandro-martinez",
            "https://www.manutd.com/en/teams/mens-team/noussair-mazraoui",
            "https://www.manutd.com/en/teams/mens-team/luke-shaw",
            "https://www.manutd.com/en/teams/mens-team/leny-yoro",
            "https://www.manutd.com/en/teams/mens-team/altay-bayindir",
            "https://www.manutd.com/en/teams/mens-team/karl-darlow",
            "https://www.manutd.com/en/teams/mens-team/tom-heaton",
            "https://www.manutd.com/en/teams/mens-team/senne-lammens",
            "https://www.manutd.com/en/teams/mens-team/dermot-mee",
            "https://www.manutd.com/en/teams/mens-team/andre-onana"
        ),
        players = listOf(
            p("Matheus Cunha", "1999-05-27", "Brazil", "ATA", 10),
            p("Amad", "2002-07-11", "Ivory Coast", "ATA", 16),
            p("Shea Lacey", "2007-04-14", "England", "ATA", 61),
            p("Bryan Mbeumo", "1999-08-07", "Cameroon", "ATA", 19),
            p("Marcus Rashford", "1997-10-31", "England", "ATA", null),
            p("Benjamin Šeško", "2003-05-31", "Slovenia", "ATA", 30),
            p("Joshua Zirkzee", "2001-05-22", "Netherlands", "ATA", 11),

            p("Toby Collyer", "2004-01-03", "England", "MEI", 43),
            p("Bruno Fernandes", "1994-09-08", "Portugal", "MEI", 8),
            p("Jack Fletcher", "2007-03-19", "England", "MEI", 38),
            p("Tyler Fletcher", "2007-03-19", "Scotland", "MEI", 39),
            p("Kobbie Mainoo", "2005-04-19", "England", "MEI", 37),
            p("Mason Mount", "1999-01-10", "England", "MEI", 7),
            p("Andrey Santos", "2004-05-03", "Brazil", "MEI", 17),
            p("Youri Tielemans", "1997-05-07", "Belgium", "MEI", 18),
            p("Manuel Ugarte", "2001-04-11", "Uruguay", "VOL", 25),

            p("Harry Amass", "2007-03-16", "England", "LAT", 41),
            p("Patrick Chinazaekpere Dorgu", "2004-10-26", "Denmark", "LAT", 13),
            p("Diogo Dalot", "1999-03-18", "Portugal", "LAT", 2),
            p("Matthijs de Ligt", "1999-08-12", "Netherlands", "ZAG", 4),
            p("Tyler Fredricson", "2005-02-23", "England", "ZAG", 33),
            p("Ayden Heaven", "2006-09-22", "England", "ZAG", 26),
            p("Harry Maguire", "1993-03-05", "England", "ZAG", 5),
            p("Lisandro Martínez", "1998-01-18", "Argentina", "ZAG", 6),
            p("Noussair Mazraoui", "1997-11-14", "Morocco", "LAT", 3),
            p("Luke Shaw", "1995-07-12", "England", "LAT", 23),
            p("Leny Yoro", "2005-11-13", "France", "ZAG", 15),

            p("Altay Bayindir", "1998-04-14", "Türkiye", "GOL", 1),
            p("Karl Darlow", "1990-10-08", "Wales", "GOL", 12),
            p("Tom Heaton", "1986-04-15", "England", "GOL", 22),
            p("Senne Lammens", "2002-07-07", "Belgium", "GOL", 31),
            p("Dermot Mee", "2002-11-20", "Northern Ireland", "GOL", 45)
        )
    )

    private fun p(
        name: String,
        birthDateIso: String,
        nationality: String,
        position: String,
        shirtNumber: Int?
    ) = EuropeanRealPlayerTemplate(
        fullName = name,
        birthDateIso = birthDateIso,
        nationality = nationality,
        position = position,
        shirtNumber = shirtNumber
    )
}
