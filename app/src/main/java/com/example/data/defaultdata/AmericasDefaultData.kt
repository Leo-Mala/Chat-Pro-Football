package com.example.data.defaultdata

import com.example.data.DefaultData.CountryData
import com.example.data.DefaultData.TeamTemplate

object AmericasDefaultData {
    val argentina = CountryData(
        continent = "América do Sul",
        firstNames = listOf("Lautaro", "Enzo", "Joaquín", "Mateo", "Thiago", "Valentín", "Bautista", "Nicolás", "Julián", "Gonzalo", "Rodrigo", "Leandro", "Alexis", "Lionel", "Ángel", "Marcos", "Lucas"),
        lastNames = listOf("Fernández", "Rodríguez", "González", "Martínez", "López", "Gómez", "Díaz", "Álvarez", "Romero", "Sosa", "Medina", "Herrera", "Blanco", "Pérez", "García", "De Paul"),
        teams = listOf(
            TeamTemplate("River Plate", "Buenos Aires", "BA", 1, 84, "Monumental de Núñez"),
            TeamTemplate("Boca Juniors", "Buenos Aires", "BA", 1, 83, "La Bombonera"),
            TeamTemplate("Racing Club", "Avellaneda", "BA", 1, 81, "El Cilindro"),
            TeamTemplate("Independiente", "Avellaneda", "BA", 1, 79, "Libertadores de América"),
            TeamTemplate("San Lorenzo", "Buenos Aires", "BA", 1, 78, "Nuevo Gasómetro"),
            TeamTemplate("Estudiantes LP", "La Plata", "BA", 1, 80, "Jorge Luis Hirschi"),
            TeamTemplate("Vélez Sarsfield", "Buenos Aires", "BA", 1, 79, "José Amalfitani"),
            TeamTemplate("Lanús", "Lanús", "BA", 1, 78, "La Fortaleza"),
            TeamTemplate("Talleres Córdoba", "Córdoba", "CB", 1, 81, "Mario Alberto Kempes"),
            TeamTemplate("Newell's Old Boys", "Rosario", "SF", 1, 77, "Marcelo Bielsa"),
            
            TeamTemplate("Rosario Central", "Rosario", "SF", 2, 75, "Gigante de Arroyito"),
            TeamTemplate("Huracán", "Buenos Aires", "BA", 2, 74, "Tomás Ducó"),
            TeamTemplate("Argentinos Juniors", "Buenos Aires", "BA", 2, 75, "Diego Maradona"),
            TeamTemplate("Belgrano", "Córdoba", "CB", 2, 73, "Julio Villagra"),
            TeamTemplate("Defensa y Justicia", "Florencio Varela", "BA", 2, 74, "Norberto Tomaghello"),
            TeamTemplate("Banfield", "Banfield", "BA", 2, 71, "Florencio Sola"),
            TeamTemplate("Gimnasia LP", "La Plata", "BA", 2, 72, "Juan Zerillo"),
            TeamTemplate("Unión Santa Fe", "Santa Fe", "SF", 2, 71, "15 de Abril"),
            TeamTemplate("Atlético Tucumán", "Tucumán", "TM", 2, 72, "José Fierro"),
            TeamTemplate("Godoy Cruz", "Mendoza", "MZ", 2, 75, "Malvinas Argentinas"),
            
            TeamTemplate("Colón de Santa Fe", "Santa Fe", "SF", 3, 66, "Brigadier López"),
            TeamTemplate("Quilmes", "Quilmes", "BA", 3, 64, "Centenario"),
            TeamTemplate("Platense", "Vicente López", "BA", 3, 65, "Ciudad de Vicente López"),
            TeamTemplate("Tigre", "Victoria", "BA", 3, 63, "José Dellagiovanna"),
            TeamTemplate("Instituto ACC", "Córdoba", "CB", 3, 65, "Monumental de Alta Córdoba"),
            TeamTemplate("Barracas Central", "Buenos Aires", "BA", 3, 63, "Claudio Tapia"),
            TeamTemplate("Central Córdoba", "Santiago", "SE", 3, 62, "Alfredo Terrera"),
            TeamTemplate("Sarmiento Junín", "Junín", "BA", 3, 61, "Eva Perón"),
            TeamTemplate("Deportivo Riestra", "Buenos Aires", "BA", 3, 60, "Guillermo Laza"),
            TeamTemplate("Independiente Riv.", "Mendoza", "MZ", 3, 62, "Bautista Gargantini"),
            
            TeamTemplate("Chacarita Juniors", "San Martín", "BA", 4, 55, "Chacarita"),
            TeamTemplate("Ferro Carril Oeste", "Buenos Aires", "BA", 4, 54, "Ricardo Etcheverri"),
            TeamTemplate("Nueva Chicago", "Buenos Aires", "BA", 4, 53, "Nueva Chicago"),
            TeamTemplate("Almirante Brown", "San Justo", "BA", 4, 52, "Fragata Sarmiento"),
            TeamTemplate("Temperley", "Temperley", "BA", 4, 51, "Alfredo Beranger"),
            TeamTemplate("Atlanta", "Buenos Aires", "BA", 4, 50, "Don León Kolbowsky"),
            TeamTemplate("Defensores Belgrano", "Buenos Aires", "BA", 4, 51, "Juan Pasquale"),
            TeamTemplate("San Martín Tucumán", "Tucumán", "TM", 4, 54, "La Ciudadela"),
            TeamTemplate("Deportivo Morón", "Morón", "BA", 4, 49, "Nuevo Urbano"),
            TeamTemplate("All Boys", "Buenos Aires", "BA", 4, 50, "Islas Malvinas")
        )
    )

    val usaMexico = CountryData(
        continent = "América do Norte / Central",
        firstNames = listOf("Christian", "Tyler", "Weston", "Walker", "Brenden", "Timothy", "Miles", "Ricardo", "Guillermo", "Henry", "Edson", "Hirving", "Luis", "César", "Uriel", "Carlos", "Guillermo"),
        lastNames = listOf("Pulisic", "McKennie", "Adams", "Robinson", "Turner", "Weah", "Reyna", "Ochoa", "Giménez", "Martín", "Álvarez", "Lozano", "Montes", "Gallardo", "Jiménez", "Sánchez"),
        teams = listOf(
            TeamTemplate("Inter Miami CF", "Miami", "FL", 1, 84, "Chase Stadium"),
            TeamTemplate("Columbus Crew", "Columbus", "OH", 1, 81, "Lower.com Field"),
            TeamTemplate("LAFC", "Los Angeles", "CA", 1, 82, "BMO Stadium"),
            TeamTemplate("LA Galaxy", "Los Angeles", "CA", 1, 81, "Dignity Health Park"),
            TeamTemplate("Club América", "Mexico City", "MEX", 1, 83, "Estadio Azteca"),
            TeamTemplate("Cruz Azul", "Mexico City", "MEX", 1, 82, "Estadio de la Ciudad"),
            TeamTemplate("Tigres UANL", "Monterrey", "MEX", 1, 82, "Estadio Universitario"),
            TeamTemplate("CF Monterrey", "Monterrey", "MEX", 1, 83, "Estadio BBVA"),
            TeamTemplate("Chivas Guadalajara", "Guadalajara", "MEX", 1, 80, "Estadio Akron"),
            TeamTemplate("Real Salt Lake", "Sandy", "UT", 1, 79, "America First Field"),
            
            TeamTemplate("Philadelphia Union", "Chester", "PA", 2, 76, "Subaru Park"),
            TeamTemplate("Atlanta United", "Atlanta", "GA", 2, 75, "Mercedes-Benz Stadium"),
            TeamTemplate("Nashville SC", "Nashville", "TN", 2, 74, "Geodis Park"),
            TeamTemplate("New England Rev", "Foxborough", "MA", 2, 73, "Gillette Stadium"),
            TeamTemplate("Portland Timbers", "Portland", "OR", 2, 74, "Providence Park"),
            TeamTemplate("Atlas FC", "Guadalajara", "MEX", 2, 75, "Estadio Jalisco"),
            TeamTemplate("Club Tijuana", "Tijuana", "MEX", 2, 74, "Estadio Caliente"),
            TeamTemplate("Club Necaxa", "Aguascalientes", "MEX", 2, 73, "Estadio Victoria"),
            TeamTemplate("Charlotte FC", "Charlotte", "NC", 2, 74, "Bank of America Stadium"),
            TeamTemplate("St. Louis City SC", "St. Louis", "MO", 2, 73, "Citypark"),
            
            TeamTemplate("New York City FC", "New York", "NY", 3, 66, "Yankee Stadium"),
            TeamTemplate("Colorado Rapids", "Commerce City", "CO", 3, 65, "Dick's Sporting Goods Park"),
            TeamTemplate("Chicago Fire FC", "Chicago", "IL", 3, 63, "Soldier Field"),
            TeamTemplate("FC Dallas", "Frisco", "TX", 3, 64, "Toyota Stadium"),
            TeamTemplate("Club Celaya", "Celaya", "MEX", 3, 65, "Estadio Miguel Alemán"),
            TeamTemplate("Atlético Morelia", "Morelia", "MEX", 3, 64, "Estadio Morelos"),
            TeamTemplate("Venados FC", "Mérida", "MEX", 3, 62, "Estadio Carlos Iturralde"),
            TeamTemplate("D.C. United", "Washington", "DC", 3, 63, "Audi Field"),
            TeamTemplate("Toronto FC", "Toronto", "ON", 3, 63, "BMO Field"),
            TeamTemplate("Sacramento Rep.", "Sacramento", "CA", 3, 60, "Heart Health Park"),
            
            TeamTemplate("Louisville City", "Louisville", "KY", 4, 56, "Lynn Family Stadium"),
            TeamTemplate("Tampa Bay Rowdies", "St. Petersburg", "FL", 4, 54, "Al Lang Stadium"),
            TeamTemplate("Charleston Battery", "Charleston", "SC", 4, 53, "Patriots Point"),
            TeamTemplate("Tampico Madero", "Tampico", "MEX", 4, 55, "Estadio Tamaulipas"),
            TeamTemplate("Irapuato FC", "Irapuato", "MEX", 4, 54, "Estadio Sergio Chávez"),
            TeamTemplate("Inter Playa", "Playa del Carmen", "MEX", 4, 52, "Estadio Mario Villanueva"),
            TeamTemplate("Indy Eleven", "Indianapolis", "IN", 4, 51, "Michael Carroll Stadium"),
            TeamTemplate("New Mexico United", "Albuquerque", "NM", 4, 51, "Isotopes Park"),
            TeamTemplate("El Paso Locomotive", "El Paso", "TX", 4, 48, "Southwest University Park"),
            TeamTemplate("Miami FC", "Miami", "FL", 4, 46, "FIU Stadium")
        )
    )

    val americaCentral = CountryData(
        continent = "América Central",
        firstNames = listOf("Celso", "Joel", "Bryan", "Keylor", "Maylor", "Alberth", "Luis", "Alex", "Devron", "Harold", "Yeltsin", "Francisco", "Ariel", "Orlando", "Freddy", "Anibal", "Cecilio"),
        lastNames = listOf("Borges", "Campbell", "Ruiz", "Navas", "Tejeda", "Elis", "Palacios", "López", "Vargas", "Ramos", "Figueroa", "Guzmán", "Pinto", "Godoy", "Waterman", "Arboleda"),
        teams = listOf(
            TeamTemplate("Deportivo Saprissa", "San José", "CRC", 1, 76, "Ricardo Saprissa"),
            TeamTemplate("LD Alajuelense", "Alajuela", "CRC", 1, 75, "Alejandro Morera Soto"),
            TeamTemplate("Herediano", "Heredia", "CRC", 1, 74, "Eladio Rosabal Cordero"),
            TeamTemplate("Olimpia", "Tegucigalpa", "HON", 1, 75, "Nacional Chelato Uclés"),
            TeamTemplate("Motagua", "Tegucigalpa", "HON", 1, 72, "Nacional Chelato Uclés"),
            TeamTemplate("Comunicaciones FC", "Guatemala", "GUA", 1, 73, "Doroteo Guamuch Flores"),
            TeamTemplate("CSD Municipal", "Guatemala", "GUA", 1, 72, "El Trébol"),
            TeamTemplate("CD FAS", "Santa Ana", "SLV", 1, 70, "Óscar Quiteño"),
            TeamTemplate("CD Águila", "San Miguel", "SLV", 1, 71, "Juan Francisco Barraza"),
            TeamTemplate("Tauro FC", "Panama City", "PAN", 1, 70, "Rommel Fernández"),
            
            TeamTemplate("CS Cartaginés", "Cartago", "CRC", 2, 69, "Fello Meza"),
            TeamTemplate("AD San Carlos", "Ciudad Quesada", "CRC", 2, 68, "Carlos Ugalde"),
            TeamTemplate("Real España", "San Pedro Sula", "HON", 2, 70, "Francisco Morazán"),
            TeamTemplate("Marathón", "San Pedro Sula", "HON", 2, 69, "Yankel Rosenthal"),
            TeamTemplate("Antigua GFC", "Antigua", "GUA", 2, 68, "Pensativo"),
            TeamTemplate("Cobán Imperial", "Cobán", "GUA", 2, 67, "José Ángel Rossi"),
            TeamTemplate("Alianza FC", "San Salvador", "SLV", 2, 69, "Cuscatlán"),
            TeamTemplate("San Francisco FC", "La Chorrera", "PAN", 2, 66, "Agustín Muquita Sanchéz"),
            TeamTemplate("CA Independiente", "La Chorrera", "PAN", 2, 68, "Agustín Muquita Sanchéz"),
            TeamTemplate("Plaza Amador", "Panama City", "PAN", 2, 67, "Maracaná de El Chorrillo"),
            
            TeamTemplate("Santos de Guápiles", "Guápiles", "CRC", 3, 63, "Ebal Rodríguez"),
            TeamTemplate("AD Guanacasteca", "Nicoya", "CRC", 3, 62, "Chorotega"),
            TeamTemplate("CDS Vida", "La Ceiba", "HON", 3, 64, "Ceibeño"),
            TeamTemplate("Olancho FC", "Juticalpa", "HON", 3, 63, "Juan Ramón Brevé"),
            TeamTemplate("Xelajú MC", "Quetzaltenango", "GUA", 3, 64, "Mario Camposeco"),
            TeamTemplate("Deportivo Guastatoya", "Guastatoya", "GUA", 3, 63, "David Cordón Hichos"),
            TeamTemplate("Santa Tecla FC", "Santa Tecla", "SLV", 3, 61, "Las Delícias"),
            TeamTemplate("Isidro Metapán", "Metapán", "SLV", 3, 62, "Jorge Calero Suárez"),
            TeamTemplate("Herrera FC", "Chitré", "PAN", 3, 61, "Los Milagros"),
            TeamTemplate("Sporting SM", "San Miguelito", "PAN", 3, 62, "Los Andes"),
            
            TeamTemplate("Puntarenas FC", "Puntarenas", "CRC", 4, 55, "Lito Pérez"),
            TeamTemplate("Municipal Liberia", "Liberia", "CRC", 4, 54, "Edgardo Baltodano"),
            TeamTemplate("CD Victoria", "La Ceiba", "HON", 4, 53, "Ceibeño"),
            TeamTemplate("Lobos UPNFM", "Choluteca", "HON", 4, 52, "Emilio Williams"),
            TeamTemplate("Deportivo Malacateco", "Malacatán", "GUA", 4, 54, "Santa Lucía"),
            TeamTemplate("Deportivo Mixco", "Mixco", "GUA", 4, 53, "Santo Domingo de Guzmán"),
            TeamTemplate("Platense Municipal", "Zacatecoluca", "SLV", 4, 51, "Antonio Toledo Valle"),
            TeamTemplate("CD Dragón", "San Miguel", "SLV", 4, 50, "Juan Francisco Barraza"),
            TeamTemplate("Potros del Este", "Panama City", "PAN", 4, 51, "Hacienda Country Club"),
            TeamTemplate("Veraguas United", "Santiago", "PAN", 4, 49, "Atalaya")
        )
    )
}
