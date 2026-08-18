package com.example.data.defaultdata

import com.example.data.DefaultData.CountryData
import com.example.data.DefaultData.TeamTemplate

/**
 * Baseline europeu pré-definido.
 *
 * Inglaterra e Espanha têm a primeira divisão alinhada à temporada factual 2026/27 nesta fase.
 * Divisões inferiores continuam parcialmente preenchidas e são completadas pelo fallback
 * determinístico legado até a transcrição oficial das subfases seguintes.
 *
 * `rating` é exclusivamente um atributo interno de gameplay; não representa nota oficial de
 * nenhuma liga, UEFA, EA Sports ou outra base proprietária.
 */
object EuropeDefaultData {
    val england = CountryData(
        continent = "Europa",
        firstNames = listOf("Harry", "John", "Jack", "Mason", "Marcus", "James", "Charlie", "George", "Oliver", "William", "Jude", "Bukayo", "Declan", "Cole", "Trent", "Phil", "Kyle"),
        lastNames = listOf("Smith", "Jones", "Taylor", "Williams", "Brown", "Davies", "Evans", "Wilson", "Thomas", "Roberts", "Kane", "Palmer", "Rice", "Saka", "Foden", "Stones"),
        teams = listOf(
            // Premier League 2026/27 — 20 participantes oficiais.
            TeamTemplate("Arsenal FC", "London", "LON", 1, 89, "Emirates Stadium"),
            TeamTemplate("Aston Villa", "Birmingham", "BIR", 1, 84, "Villa Park"),
            TeamTemplate("AFC Bournemouth", "Bournemouth", "BOU", 1, 78, "Vitality Stadium"),
            TeamTemplate("Brentford FC", "London", "LON", 1, 78, "Gtech Community Stadium"),
            TeamTemplate("Brighton & Hove Albion", "Brighton", "BHA", 1, 80, "Amex Stadium"),
            TeamTemplate("Chelsea FC", "London", "LON", 1, 86, "Stamford Bridge"),
            TeamTemplate("Coventry City", "Coventry", "COV", 1, 73, "Coventry Building Society Arena"),
            TeamTemplate("Crystal Palace", "London", "LON", 1, 80, "Selhurst Park"),
            TeamTemplate("Everton FC", "Liverpool", "LIV", 1, 77, "Hill Dickinson Stadium"),
            TeamTemplate("Fulham FC", "London", "LON", 1, 79, "Craven Cottage"),
            TeamTemplate("Hull City", "Hull", "HUL", 1, 72, "MKM Stadium"),
            TeamTemplate("Ipswich Town", "Ipswich", "IPS", 1, 73, "Portman Road"),
            TeamTemplate("Leeds United", "Leeds", "LEE", 1, 77, "Elland Road"),
            TeamTemplate("Liverpool FC", "Liverpool", "LIV", 1, 88, "Anfield"),
            TeamTemplate("Manchester City", "Manchester", "MNC", 1, 89, "Etihad Stadium"),
            TeamTemplate("Manchester United", "Manchester", "MNC", 1, 83, "Old Trafford"),
            TeamTemplate("Newcastle United", "Newcastle", "NEW", 1, 84, "St James' Park"),
            TeamTemplate("Nottingham Forest", "Nottingham", "NOT", 1, 79, "City Ground"),
            TeamTemplate("Sunderland AFC", "Sunderland", "SUN", 1, 75, "Stadium of Light"),
            TeamTemplate("Tottenham Hotspur", "London", "LON", 1, 83, "Tottenham Hotspur Stadium"),

            // Championship: cobertura ainda parcial nesta fatia; o restante continua em fallback.
            TeamTemplate("West Ham United", "London", "LON", 2, 78, "London Stadium"),
            TeamTemplate("Burnley", "Burnley", "BUR", 2, 75, "Turf Moor"),
            TeamTemplate("Wolverhampton Wanderers", "Wolverhampton", "WOL", 2, 76, "Molineux Stadium"),
            TeamTemplate("Leicester City", "Leicester", "LEI", 2, 76, "King Power Stadium"),
            TeamTemplate("Southampton FC", "Southampton", "SOU", 2, 74, "St Mary's Stadium"),
            TeamTemplate("West Bromwich Albion", "West Bromwich", "WBA", 2, 72, "The Hawthorns"),
            TeamTemplate("Norwich City", "Norwich", "NOR", 2, 71, "Carrow Road"),
            TeamTemplate("Middlesbrough", "Middlesbrough", "MID", 2, 71, "Riverside Stadium"),

            // League One: cobertura parcial herdada; será transcrita em checkpoint próprio.
            TeamTemplate("Derby County", "Derby", "DER", 3, 65, "Pride Park Stadium"),
            TeamTemplate("Portsmouth FC", "Portsmouth", "POR", 3, 66, "Fratton Park"),
            TeamTemplate("Bolton Wanderers", "Bolton", "BOL", 3, 64, "Toughsheet Stadium"),
            TeamTemplate("Peterborough Utd", "Peterborough", "PET", 3, 63, "Weston Homes Stadium"),
            TeamTemplate("Barnsley FC", "Barnsley", "BAR", 3, 62, "Oakwell"),
            TeamTemplate("Oxford United", "Oxford", "OXF", 3, 61, "Kassam Stadium"),
            TeamTemplate("Lincoln City", "Lincoln", "LIN", 3, 60, "Sincil Bank"),
            TeamTemplate("Blackpool FC", "Blackpool", "BLA", 3, 62, "Bloomfield Road"),
            TeamTemplate("Reading FC", "Reading", "REA", 3, 61, "Select Car Leasing Stadium"),
            TeamTemplate("Wigan Athletic", "Wigan", "WIG", 3, 60, "DW Stadium"),

            // Templates de nível 4 permanecem disponíveis para futura expansão da hierarquia.
            TeamTemplate("Wrexham AFC", "Wrexham", "WRX", 4, 56, "Racecourse Ground"),
            TeamTemplate("Stockport County", "Stockport", "STK", 4, 53, "Edgeley Park"),
            TeamTemplate("Mansfield Town", "Mansfield", "MAN", 4, 52, "Field Mill"),
            TeamTemplate("MK Dons", "Milton Keynes", "MKD", 4, 51, "Stadium MK"),
            TeamTemplate("Crewe Alexandra", "Crewe", "CRW", 4, 49, "Gresty Road"),
            TeamTemplate("Crawley Town", "Crawley", "CRA", 4, 48, "Broadfield Stadium"),
            TeamTemplate("Bradford City", "Bradford", "BRA", 4, 51, "Valley Parade"),
            TeamTemplate("Notts County", "Nottingham", "NOT", 4, 50, "Meadow Lane"),
            TeamTemplate("Gillingham FC", "Gillingham", "GIL", 4, 48, "Priestfield Stadium"),
            TeamTemplate("AFC Wimbledon", "London", "LON", 4, 49, "Plough Lane")
        )
    )

    val spain = CountryData(
        continent = "Europa",
        firstNames = listOf("Gavi", "Pedri", "Nico", "Lamine", "Alvaro", "Dani", "Ferran", "Alejandro", "Pau", "Marc", "Ansu", "Rodri", "Unai", "Robin", "Aymeric", "Mikel", "Martin"),
        lastNames = listOf("González", "Sánchez", "Martínez", "López", "Gómez", "Díaz", "Álvarez", "Torres", "Carvajal", "Yamal", "Williams", "Olmo", "Cubarsí", "Merino", "Zubimendi"),
        teams = listOf(
            // La Liga 2026/27 — 20 participantes oficiais.
            TeamTemplate("Athletic Club", "Bilbao", "BIL", 1, 83, "San Mamés"),
            TeamTemplate("Atlético de Madrid", "Madrid", "MDR", 1, 86, "Metropolitano"),
            TeamTemplate("CA Osasuna", "Pamplona", "PAM", 1, 77, "El Sadar"),
            TeamTemplate("Celta de Vigo", "Vigo", "VIG", 1, 78, "Balaídos"),
            TeamTemplate("Deportivo Alavés", "Vitoria-Gasteiz", "VIT", 1, 75, "Mendizorrotza"),
            TeamTemplate("Elche CF", "Elche", "ELC", 1, 74, "Martínez Valero"),
            TeamTemplate("FC Barcelona", "Barcelona", "BCN", 1, 90, "Camp Nou"),
            TeamTemplate("Getafe CF", "Getafe", "GET", 1, 75, "Coliseum"),
            TeamTemplate("Levante UD", "Valencia", "VAL", 1, 74, "Ciutat de València"),
            TeamTemplate("Málaga CF", "Málaga", "MAL", 1, 72, "La Rosaleda"),
            TeamTemplate("Racing Santander", "Santander", "SAN", 1, 73, "El Sardinero"),
            TeamTemplate("Rayo Vallecano", "Madrid", "MDR", 1, 77, "Vallecas"),
            TeamTemplate("RC Deportivo", "A Coruña", "COR", 1, 72, "Riazor"),
            TeamTemplate("RCD Espanyol de Barcelona", "Barcelona", "BCN", 1, 76, "RCDE Stadium"),
            TeamTemplate("Real Betis", "Sevilla", "SEV", 1, 82, "Benito Villamarín"),
            TeamTemplate("Real Madrid", "Madrid", "MDR", 1, 92, "Santiago Bernabéu"),
            TeamTemplate("Real Sociedad", "San Sebastián", "SSG", 1, 81, "Anoeta"),
            TeamTemplate("Sevilla FC", "Sevilla", "SEV", 1, 79, "Ramón Sánchez-Pizjuán"),
            TeamTemplate("Valencia CF", "Valencia", "VAL", 1, 78, "Mestalla"),
            TeamTemplate("Villarreal CF", "Villarreal", "VIL", 1, 82, "Estadio de la Cerámica"),

            // Segunda División: cobertura parcial nesta fatia.
            TeamTemplate("Girona FC", "Girona", "GIR", 2, 77, "Montilivi"),
            TeamTemplate("Real Zaragoza", "Zaragoza", "ZAR", 2, 73, "La Romareda"),
            TeamTemplate("Real Valladolid", "Valladolid", "VAL", 2, 74, "José Zorrilla"),
            TeamTemplate("SD Eibar", "Eibar", "EIB", 2, 72, "Ipurua"),
            TeamTemplate("CD Leganés", "Leganés", "LEG", 2, 73, "Butarque"),
            TeamTemplate("Sporting de Gijón", "Gijón", "GIJ", 2, 71, "El Molinón"),
            TeamTemplate("CD Tenerife", "Tenerife", "TEN", 2, 70, "Heliodoro Rodríguez López"),
            TeamTemplate("Real Oviedo", "Oviedo", "OVI", 2, 72, "Carlos Tartiere"),

            // Terceiro nível: cobertura parcial herdada.
            TeamTemplate("Castellón", "Castellón", "CAS", 3, 65, "Castalia"),
            TeamTemplate("Ibiza", "Ibiza", "IBZ", 3, 64, "Can Misses"),
            TeamTemplate("Córdoba CF", "Córdoba", "COR", 3, 65, "El Arcángel"),
            TeamTemplate("Recreativo Huelva", "Huelva", "HUE", 3, 63, "Nuevo Colombino"),
            TeamTemplate("Real Murcia", "Murcia", "MUR", 3, 62, "Nueva Condomina"),
            TeamTemplate("Ponferradina", "Ponferrada", "PON", 3, 63, "El Toralín"),
            TeamTemplate("Algeciras CF", "Algeciras", "ALG", 3, 60, "Nuevo Mirador"),
            TeamTemplate("AD Ceuta", "Ceuta", "CEU", 3, 61, "Alfonso Murube"),

            TeamTemplate("Sestao River", "Sestao", "SES", 4, 55, "Las Llanas"),
            TeamTemplate("Teruel", "Teruel", "TER", 4, 54, "Pinilla"),
            TeamTemplate("Tarazona", "TAR", "TAR", 4, 52, "Municipal de Tarazona"),
            TeamTemplate("Arenteiro", "Carballiño", "ARE", 4, 53, "Espiñedo"),
            TeamTemplate("Sabadell", "Sabadell", "SAB", 4, 54, "Nova Creu Alta"),
            TeamTemplate("CD Lugo", "Lugo", "LUG", 4, 55, "Anxo Carro"),
            TeamTemplate("Unionistas CF", "Salamanca", "SAL", 4, 53, "Reina Sofía"),
            TeamTemplate("Rayo Majadahonda", "Majadahonda", "MAJ", 4, 51, "Cerro del Espino"),
            TeamTemplate("UD Melilla", "Melilla", "MEL", 4, 50, "Álvarez Claro"),
            TeamTemplate("UD Logroñés", "Logroño", "LOG", 4, 52, "Las Gaunas")
        )
    )
}
