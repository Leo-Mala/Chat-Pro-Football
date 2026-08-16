package com.example.data.defaultdata

import com.example.data.DefaultData.CountryData
import com.example.data.DefaultData.TeamTemplate

object EuropeDefaultData {
    val england = CountryData(
        continent = "Europa",
        firstNames = listOf("Harry", "John", "Jack", "Mason", "Marcus", "James", "Charlie", "George", "Oliver", "William", "Jude", "Bukayo", "Declan", "Cole", "Trent", "Phil", "Kyle"),
        lastNames = listOf("Smith", "Jones", "Taylor", "Williams", "Brown", "Davies", "Evans", "Wilson", "Thomas", "Roberts", "Kane", "Palmer", "Rice", "Saka", "Foden", "Stones"),
        teams = listOf(
            TeamTemplate("Manchester City", "Manchester", "MNC", 1, 88, "Etihad Stadium"),
            TeamTemplate("Arsenal FC", "London", "LON", 1, 86, "Emirates Stadium"),
            TeamTemplate("Liverpool FC", "Liverpool", "LIV", 1, 87, "Anfield"),
            TeamTemplate("Chelsea FC", "London", "LON", 1, 83, "Stamford Bridge"),
            TeamTemplate("Manchester United", "Manchester", "MNC", 1, 82, "Old Trafford"),
            TeamTemplate("Tottenham Hotspur", "London", "LON", 1, 81, "Tottenham Stadium"),
            TeamTemplate("Aston Villa", "Birmingham", "BIR", 1, 82, "Villa Park"),
            TeamTemplate("Newcastle United", "Newcastle", "NEW", 1, 81, "St James' Park"),
            TeamTemplate("West Ham United", "London", "LON", 1, 79, "London Stadium"),
            TeamTemplate("Everton FC", "Liverpool", "LIV", 1, 77, "Goodison Park"),
            
            TeamTemplate("Leicester City", "Leicester", "LEI", 2, 76, "King Power Stadium"),
            TeamTemplate("Leeds United", "Leeds", "LEE", 2, 75, "Elland Road"),
            TeamTemplate("Southampton FC", "Southampton", "SOU", 2, 74, "St Mary's Stadium"),
            TeamTemplate("Ipswich Town", "Ipswich", "IPS", 2, 73, "Portman Road"),
            TeamTemplate("West Bromwich", "West Bromwich", "WBA", 2, 72, "The Hawthorns"),
            TeamTemplate("Norwich City", "Norwich", "NOR", 2, 71, "Carrow Road"),
            TeamTemplate("Coventry City", "Coventry", "COV", 2, 70, "Coventry Arena"),
            TeamTemplate("Middlesbrough", "Middlesbrough", "MID", 2, 71, "Riverside Stadium"),
            TeamTemplate("Hull City", "Hull", "HUL", 2, 69, "MKM Stadium"),
            TeamTemplate("Sunderland AFC", "Sunderland", "SUN", 2, 70, "Stadium of Light"),
            
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
            TeamTemplate("Real Madrid", "Madrid", "MDR", 1, 89, "Santiago Bernabéu"),
            TeamTemplate("Barcelona", "Barcelona", "BCN", 1, 87, "Camp Nou"),
            TeamTemplate("Atlético de Madrid", "Madrid", "MDR", 1, 84, "Cívitas Metropolitano"),
            TeamTemplate("Girona", "Girona", "GIR", 1, 80, "Montilivi"),
            TeamTemplate("Real Sociedad", "San Sebastián", "SSG", 1, 81, "Reale Arena"),
            TeamTemplate("Athletic Bilbao", "Bilbao", "BIL", 1, 82, "San Mamés"),
            TeamTemplate("Real Betis", "Seville", "SEV", 1, 80, "Benito Villamarín"),
            TeamTemplate("Villarreal", "Villarreal", "VIL", 1, 79, "Estádio de la Cerâmica"),
            TeamTemplate("Sevilla FC", "Seville", "SEV", 1, 78, "Ramón Sánchez Pizjuán"),
            TeamTemplate("Valencia CF", "Valencia", "VAL", 1, 77, "Mestalla"),
            
            TeamTemplate("Espanyol", "Barcelona", "BCN", 2, 75, "Stage Front Stadium"),
            TeamTemplate("Real Zaragoza", "Zaragoza", "ZAR", 2, 73, "La Romareda"),
            TeamTemplate("Real Valladolid", "Valladolid", "VAL", 2, 74, "José Zorrilla"),
            TeamTemplate("Eibar", "Eibar", "EIB", 2, 72, "Ipurua"),
            TeamTemplate("Leganés", "Leganés", "LEG", 2, 73, "Butarque"),
            TeamTemplate("Sporting Gijón", "Gijón", "GIJ", 2, 71, "El Molinón"),
            TeamTemplate("Levante UD", "Valencia", "VAL", 2, 72, "Ciutat de València"),
            TeamTemplate("Elche CF", "Elche", "ELC", 2, 71, "Martínez Valero"),
            TeamTemplate("Tenerife", "Tenerife", "TEN", 2, 70, "Heliodoro Rodríguez"),
            TeamTemplate("Real Oviedo", "Oviedo", "OVI", 2, 72, "Carlos Tartiere"),
            
            TeamTemplate("Deportivo La Coruña", "La Coruña", "COR", 3, 67, "Riazor"),
            TeamTemplate("Málaga CF", "Málaga", "MAL", 3, 66, "La Rosaleda"),
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
            TeamTemplate("Tarazona", "Tarazona", "TAR", 4, 52, "Municipal de Tarazona"),
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
