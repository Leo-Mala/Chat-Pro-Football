package com.example.data

import com.example.data.defaultdata.AmericasDefaultData
import com.example.data.defaultdata.BrazilDefaultData
import com.example.data.defaultdata.EuropeDefaultData
import com.example.data.defaultdata.WorldDefaultData
import kotlin.random.Random

object DefaultData {
    data class TeamTemplate(
        val name: String,
        val city: String,
        val state: String,
        val division: Int, // 1 to 4
        val rating: Int, // 1 to 100
        val stadium: String
    )

    data class CountryData(
        val continent: String,
        val firstNames: List<String>,
        val lastNames: List<String>,
        val teams: List<TeamTemplate>
    )

    val originalMap = mapOf(
        "Brasil" to BrazilDefaultData.data,
        "Argentina" to AmericasDefaultData.argentina,
        "Inglaterra" to EuropeDefaultData.england,
        "Estados Unidos / México" to AmericasDefaultData.usaMexico,
        "Espanha" to EuropeDefaultData.spain,
        "América Central" to AmericasDefaultData.americaCentral,
        "África" to WorldDefaultData.africa,
        "Ásia" to WorldDefaultData.asia,
        "Oceania" to WorldDefaultData.oceania,
        "África / Ásia / Oceania" to WorldDefaultData.africaAsiaOceania
    )

    val countryDivisionSizes = mapOf(
        "Inglaterra" to listOf(20, 24, 24),
        "Espanha" to listOf(20, 22, 40),
        "Itália" to listOf(20, 20, 60),
        "Alemanha" to listOf(18, 18, 20),
        "França" to listOf(18, 18, 17),
        "Portugal" to listOf(18, 18, 18),
        "Países Baixos" to listOf(18, 20, 18),
        "Bélgica" to listOf(16, 16, 18),
        "Turquia" to listOf(19, 20, 38),
        "Escócia" to listOf(12, 10, 10),
        "Áustria" to listOf(12, 16, 16),
        "Suíça" to listOf(12, 10),
        "Dinamarca" to listOf(12, 12, 12),
        "Noruega" to listOf(16, 16),
        "Suécia" to listOf(16, 16),
        "Polônia" to listOf(18, 18),
        "Tchéquia" to listOf(16, 16, 48),
        "Croácia" to listOf(10, 12, 56),
        "Sérvia" to listOf(16, 16, 18),
        "Grécia" to listOf(14, 14),
        "Brasil" to listOf(20, 20, 20, 96, 15),
        "Argentina" to listOf(30, 36, 40),
        "Colômbia" to listOf(20, 16, 32),
        "Chile" to listOf(16, 16, 32),
        "Uruguai" to listOf(16, 14, 14),
        "Paraguai" to listOf(12, 12, 12),
        "Equador" to listOf(16, 10),
        "Peru" to listOf(19, 14),
        "Bolívia" to listOf(16),
        "Venezuela" to listOf(14, 14),
        "México" to listOf(18, 15, 57),
        "Estados Unidos / Canadá" to listOf(30, 24, 24),
        "Costa Rica" to listOf(12, 18, 16),
        "Guatemala" to listOf(12, 20),
        "Honduras" to listOf(10, 10),
        "Panamá" to listOf(11, 10),
        "El Salvador" to listOf(12, 16),
        "Jamaica" to listOf(14, 20),
        "República Dominicana" to listOf(10),
        "Trinidad e Tobago" to listOf(12),
        "Japão" to listOf(20, 20, 20),
        "Coreia do Sul" to listOf(12, 13, 27),
        "Arábia Saudita" to listOf(18, 18, 18),
        "Emirados Árabes Unidos" to listOf(14, 12),
        "Catar" to listOf(12, 12),
        "Irã" to listOf(16, 18, 28),
        "China" to listOf(16, 16, 36),
        "Austrália" to listOf(13, 12),
        "Egito" to listOf(16, 18),
        "Marrocos" to listOf(16, 16, 16),
        "Tunísia" to listOf(16, 14),
        "África do Sul" to listOf(16, 16)
    )

    val majorTransferMarketCountries = listOf(
        "Brasil", "Argentina", "Inglaterra", "Espanha", "Itália",
        "Alemanha", "França", "Portugal", "Países Baixos", "Arábia Saudita",
        "Estados Unidos / Canadá", "Japão", "México"
    )

    val countryCities = mapOf(
        "Inglaterra" to listOf("London", "Manchester", "Liverpool", "Birmingham", "Leeds", "Newcastle", "Leicester", "Bristol", "Nottingham", "Sheffield", "Southampton", "Norwich", "Brighton", "Wolverhampton", "Bournemouth", "Crystal Palace", "Fulham", "Ipswich", "Derby", "Portsmouth", "Blackburn", "Burnley", "Luton", "Coventry", "Middlesbrough", "Sunderland", "Wrexham", "Bolton", "Peterborough", "Barnsley", "Oxford", "Lincoln", "Blackpool", "Reading", "Wigan"),
        "Espanha" to listOf("Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao", "Zaragoza", "Malaga", "Murcia", "Las Palmas", "Palma", "Vigo", "Granada", "San Sebastián", "Girona", "Villarreal", "Pamplona", "Getafe", "Vitoria", "Vallecas", "Leganés", "Valladolid", "Espanyol", "Eibar", "Gijón", "Elche", "Tenerife", "Oviedo", "Cádiz", "Deportivo", "Castellón", "Ibiza", "Córdoba", "Huelva", "Burgos", "Santander"),
        "Itália" to listOf("Roma", "Milano", "Napoli", "Torino", "Palermo", "Genova", "Bologna", "Firenze", "Bari", "Catania", "Verona", "Cagliari", "Lecce", "Udine", "Monza", "Empoli", "Salerno", "Venezia", "Sassuolo", "Frosinone", "Parma", "Como", "Cremona", "Spezia", "Pisa", "Brescia", "Reggio Emilia", "Ferrara", "Padova", "Perugia", "Livorno", "Vicenza", "Ancona", "Trieste", "Taranto"),
        "Alemanha" to listOf("Berlin", "Munchen", "Hamburg", "Koln", "Frankfurt", "Stuttgart", "Dusseldorf", "Dortmund", "Leipzig", "Bremen", "Hannover", "Nuremberg", "Leverkusen", "Monchengladbach", "Wolfsburg", "Hoffenheim", "Augsburg", "Mainz", "Freiburg", "Heidenheim", "Darmstadt", "Bochum", "Schalke", "Hertha", "Kaiserslautern", "Karlsruhe", "Rostock", "Dresden", "Bielefeld", "Ingolstadt"),
        "França" to listOf("Paris", "Marseille", "Lyon", "Toulouse", "Nice", "Nantes", "Strasbourg", "Montpellier", "Bordeaux", "Lille", "Rennes", "Reims", "Monaco", "Lens", "Brest", "Le Havre", "Metz", "Lorient", "Clermont", "Auxerre", "Angers", "Saint-Etienne", "Troyes", "Dijon", "Sochaux", "Nancy", "Valenciennes", "Ajaccio", "Bastia", "Guingamp"),
        "Portugal" to listOf("Lisboa", "Porto", "Braga", "Coimbra", "Funchal", "Setubal", "Faro", "Guimaraes", "Aveiro", "Viseu", "Portimao", "Chaves", "Estoril", "Moreira", "Arouca", "Famalicao", "Barcelos", "Rio Ave", "Vila do Conde", "Amadora", "Farense", "Boavista", "Gil Vicente", "Vizela", "Santa Clara", "Nacional", "Feirense", "Leiria", "Penafiel", "Varzim"),
        "Países Baixos" to listOf("Amsterdam", "Rotterdam", "Den Haag", "Utrecht", "Eindhoven", "Groningen", "Arnhem", "Enschede", "Tilburg", "Breda", "Alkmaar", "Nijmegen", "Heerenveen", "Zwolle", "Almere", "Sittard", "Waalwijk", "Volendam", "Emmen", "Leeuwarden", "Maastricht", "Kerkrade", "Venlo", "Deventer", "Doetinchem"),
        "Bélgica" to listOf("Brussels", "Antwerp", "Gent", "Charleroi", "Liege", "Brugge", "Namur", "Mons", "Leuven", "Mechelen", "Anderlecht", "Genk", "Kortrijk", "Eupen", "Westerlo", "Sint-Truiden", "Cercle", "Union SG", "Molenbeek", "Beveren", "Lierse", "Waregem", "Oostende"),
        "Turquia" to listOf("Istanbul", "Ankara", "Izmir", "Bursa", "Adana", "Gaziantep", "Konya", "Antalya", "Kayseri", "Trabzon", "Sivas", "Rize", "Galatasaray", "Fenerbahce", "Besiktas", "Kasimpasa", "Basaksehir", "Alanya", "Hatay", "Samsun", "Ankaragucu", "Karagumruk", "Giresun", "Göztepe", "Denizli", "Genclerbirligi"),
        "Escócia" to listOf("Glasgow", "Edinburgh", "Aberdeen", "Dundee", "Inverness", "Perth", "Paisley", "Falkirk", "Celtic", "Rangers", "Hearts", "Hibernian", "Motherwell", "Kilmarnock", "St Mirren", "Ross County", "Livingston", "St Johnstone"),
        "Áustria" to listOf("Wien", "Salzburg", "Graz", "Linz", "Innsbruck", "Klagenfurt", "Villach", "Wels", "Rapid", "Austria", "Sturm", "LASK", "Hartberg", "Wolfsberg", "Altach", "Lustenau", "Amstetten", "Floridsdorf"),
        "Suíça" to listOf("Zurich", "Geneva", "Basel", "Lausanne", "Bern", "Winterthur", "Lucerne", "St. Gallen", "Young Boys", "Grasshopper", "Servette", "Lugano", "Sion", "Yverdon", "Thun", "Aarau", "Vaduz", "Wil"),
        "Dinamarca" to listOf("Copenhagen", "Aarhus", "Odense", "Aalborg", "Esbjerg", "Randers", "Kolding", "Horsens", "Brondby", "Midtjylland", "Nordsjælland", "Silkeborg", "Viborg", "Vejle", "Hvidovre", "Lyngby", "SønderjyskE", "Fredericia"),
        "Noruega" to listOf("Oslo", "Bergen", "Trondheim", "Stavanger", "Bodo", "Tromso", "Kristiansand", "Alesund", "Glimt", "Molde", "Lillestrom", "Valerenga", "Sandefjord", "HamKam", "Sarpsborg", "Haugesund", "Odd", "Stabæk"),
        "Suécia" to listOf("Stockholm", "Goteborg", "Malmo", "Uppsala", "Vasteras", "Orebro", "Linkoping", "Helsingborg", "AIK", "Djurgården", "Hammarby", "Elfsborg", "Häcken", "Norrköping", "Halmstad", "Kalmar", "Sirius", "Mjällby", "Degerfors", "Varberg"),
        "Polônia" to listOf("Warszawa", "Krakow", "Lodz", "Wroclaw", "Poznan", "Gdansk", "Szczecin", "Bydgoszcz", "Legia", "Lech", "Rakow", "Pogon", "Slask", "Widzew", "Ruch", "Piast", "Gornik", "Jagiellonia", "Zaglebie", "Radomiak"),
        "Tchéquia" to listOf("Praha", "Brno", "Ostrava", "Plzen", "Olomouc", "Liberec", "Ceske Budejovice", "Hradec Kralove", "Sparta", "Slavia", "Viktoria", "Banik", "Slovan", "Mlada Boleslav", "Teplice", "Jablonec", "Pardubice", "Bohemians"),
        "Croácia" to listOf("Zagreb", "Split", "Rijeka", "Osijek", "Zadar", "Pula", "Slavonski Brod", "Karlovac", "Dinamo", "Hajduk", "Lokomotiva", "Gorica", "Varaždin", "Slaven Belupo", "Istra", "Rudeš", "Šibenik", "Cibalia"),
        "Sérvia" to listOf("Beograd", "Novi Sad", "Nis", "Kragujevac", "Subotica", "Leskovac", "Krusevac", "Pancevo", "Crvena Zvezda", "Partizan", "Vojvodina", "Čukarički", "TSC Bačka Palanka", "Novi Pazar", "Voždovac", "Radnički", "Napredak", "Javor"),
        "Grécia" to listOf("Athina", "Thessaloniki", "Patra", "Larisa", "Heraklion", "Volos", "Ioannina", "Chania", "Olympiacos", "Panathinaikos", "AEK", "PAOK", "Aris", "Asteras", "Atromitos", "Lamia", "OFI", "Panserraikos"),
        "Brasil" to listOf("São Paulo", "Rio de Janeiro", "Belo Horizonte", "Porto Alegre", "Curitiba", "Salvador", "Recife", "Fortaleza", "Brasília", "Goiânia", "Santos", "Manaus", "Chapecoense", "Caldas Novas", "Aracaju", "Campinas", "Belém", "Florianópolis"),
        "Argentina" to listOf("Buenos Aires", "Rosario", "Córdoba", "Mendoza", "La Plata", "Tucumán", "Salta", "Santa Fe", "Mar del Plata", "San Juan", "Lanús", "Avellaneda", "Banfield", "Quilmes", "Tigre"),
        "Colômbia" to listOf("Bogotá", "Medellín", "Cali", "Barranquilla", "Cartagena", "Bucaramanga", "Pereira", "Ibagué", "Manizales", "Santa Marta", "Cúcuta", "Pastos", "Tunja", "Neiva"),
        "Chile" to listOf("Santiago", "Valparaíso", "Concepción", "Coquimbo", "Antofagasta", "Temuco", "Rancagua", "Talca", "Arica", "Iquique", "Calama", "La Serena", "Chillán", "Curicó"),
        "Uruguai" to listOf("Montevideo", "Salto", "Paysandú", "Maldonado", "Rivera", "Tacuarembó", "Melo", "Artigas", "Las Piedras", "Colonia", "Florida", "San José", "Minas", "Mercedes"),
        "Paraguai" to listOf("Asunción", "Ciudad del Este", "Luque", "San Lorenzo", "Capiatá", "Lambaré", "Fernando", "Villarrica", "Pedro Juan Caballero", "Encarnación", "Caaguazú", "Itauguá"),
        "Equador" to listOf("Quito", "Guayaquil", "Cuenca", "Santo Domingo", "Machala", "Manta", "Portoviejo", "Loja", "Ambato", "Riobamba", "Ibarra", "Esmeraldas", "Babahoyo", "Tulcán"),
        "Peru" to listOf("Lima", "Arequipa", "Trujillo", "Chiclayo", "Piura", "Iquitos", "Cusco", "Huancayo", "Chimbote", "Tacna", "Pucallpa", "Juliaca", "Ica", "Cajamarca"),
        "Bolívia" to listOf("La Paz", "Santa Cruz", "Cochabamba", "Oruro", "Sucre", "Potosí", "Tarija", "Trinidad", "Sacaba", "Montero", "Quillacollo", "Riberalta", "Yacuiba", "Warnes"),
        "Venezuela" to listOf("Caracas", "Maracaibo", "Valencia", "Barquisimeto", "Maracay", "San Cristóbal", "Mérida", "Barcelona", "Ciudad Guayana", "Maturín", "Barinas", "Valera"),
        "México" to listOf("Ciudad de México", "Guadalajara", "Monterrey", "Puebla", "Tijuana", "León", "Juárez", "Torreón", "Querétaro", "Pachuca", "San Luis", "Mazatlán", "Necaxa", "Toluca"),
        "Estados Unidos / Canadá" to listOf("New York", "Los Angeles", "Chicago", "Toronto", "Vancouver", "Montreal", "Miami", "Seattle", "Atlanta", "Boston", "Houston", "Philadelphia", "Columbus", "Portland", "Nashville", "Orlando", "Cincinnati", "San Jose"),
        "Costa Rica" to listOf("San José", "Alajuela", "Cartago", "Heredia", "Puntarenas", "Limón", "Liberia", "San Isidro", "Guápiles", "Quesada", "Nicoya", "Paraíso"),
        "Guatemala" to listOf("Ciudad de Guatemala", "Quetzaltenango", "Escuintla", "Puerto Barrios", "Cobán", "Chimaltenango", "Antigua", "Mazatenango", "Zacapa", "Jalapa"),
        "Honduras" to listOf("Tegucigalpa", "San Pedro Sula", "La Ceiba", "El Progreso", "Choluteca", "Comayagua", "Juticalpa", "Tocoa", "Olanchito", "Siguatepeque"),
        "Panamá" to listOf("Ciudad de Panamá", "San Miguelito", "Tocumen", "David", "Las Cumbres", "Colón", "La Chorrera", "Penonomé", "Santiago de Veraguas", "Chitré"),
        "El Salvador" to listOf("San Salvador", "Santa Ana", "San Miguel", "Mejicanos", "Soyapango", "Santa Tecla", "Apopa", "Delgado", "Sonsonate", "Usulután"),
        "Jamaica" to listOf("Kingston", "Spanish Town", "Portmore", "Montego Bay", "Mandeville", "May Pen", "Savanna-la-Mar", "Port Antonio", "St. Ann's Bay", "Port Maria"),
        "República Dominicana" to listOf("Santo Domingo", "Santiago", "La Romana", "San Pedro", "San Francisco", "Puerto Plata", "Higüey", "Moca", "La Vega", "San Cristóbal"),
        "Trinidad e Tobago" to listOf("Port of Spain", "Chaguanas", "San Fernando", "Arima", "Point Fortin", "Scarborough", "Sangre Grande", "Siparia", "Tunapuna"),
        "Japão" to listOf("Tokyo", "Yokohama", "Osaka", "Nagoya", "Sapporo", "Kobe", "Fukuoka", "Kyoto", "Saitama", "Hiroshima", "Kawasaki", "Sendai", "Chiba", "Shizuoka"),
        "Coreia do Sul" to listOf("Seoul", "Busan", "Incheon", "Daegu", "Daejeon", "Gwangju", "Suwon", "Ulsan", "Jeonju", "Jeju", "Pohang", "Seongnam", "Chuncheon", "Ansan"),
        "Arábia Saudita" to listOf("Riyadh", "Jeddah", "Mecca", "Medina", "Dammam", "Taif", "Khobar", "Tabuk", "Buraidah", "Khamis Mushait", "Hofuf", "Hail", "Najran", "Abha"),
        "Emirados Árabes Unidos" to listOf("Dubai", "Abu Dhabi", "Sharjah", "Al Ain", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain", "Khor Fakkan", "Kalba"),
        "Catar" to listOf("Doha", "Al Rayyan", "Al Wakrah", "Al Khor", "Umm Salal", "Madinat ash Shamal", "Al Shahaniya", "Al Daayen", "Mesaieed"),
        "Irã" to listOf("Tehran", "Mashhad", "Isfahan", "Karaj", "Shiraz", "Tabriz", "Qom", "Ahvaz", "Kermanshah", "Urmia", "Zahedan", "Rasht", "Kerman", "Yazd"),
        "China" to listOf("Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Wuhan", "Chengdu", "Tianjin", "Nanjing", "Chongqing", "Hangzhou", "Xi'an", "Shenyang", "Harbin", "Dalian"),
        "Austrália" to listOf("Sydney", "Melbourne", "Brisbane", "Perth", "Adelaide", "Canberra", "Hobart", "Darwin", "Newcastle", "Central Coast", "Geelong", "Townsville"),
        "Egito" to listOf("Cairo", "Alexandria", "Giza", "Shubra El Kheima", "Port Said", "Suez", "Luxor", "Mansoura", "Tanta", "Asyut", "Fayoum", "Zagazig", "Aswan", "Damietta"),
        "Marrocos" to listOf("Casablanca", "Rabat", "Fes", "Marrakech", "Tangier", "Agadir", "Oujda", "Kenitra", "Tetouan", "Safi", "Meknes", "Nador", "Laayoune", "Khouribga"),
        "Tunísia" to listOf("Tunis", "Sfax", "Sousse", "Kairouan", "Bizerte", "Gabes", "Ariana", "Gafsa", "Monastir", "Hammamet", "La Marsa", "Nabeul", "Djerba", "Kasserine"),
        "África do Sul" to listOf("Johannesburg", "Cape Town", "Durban", "Pretoria", "Port Elizabeth", "Bloemfontein", "Nelspruit", "Polokwane", "Rustenburg", "East London", "Kimberley", "Pietermaritzburg")
    )

    fun countryStateCode(country: String, random: java.util.Random = java.util.Random(country.hashCode().toLong() and 0x7FFFFFFF)): String {
        val list = when (country) {
            "Brasil" -> listOf("SP", "RJ", "MG", "RS", "PR", "BA", "CE")
            "Argentina" -> listOf("BA", "SF", "CB", "MZ", "TM")
            "Inglaterra" -> listOf("LON", "MNC", "LIV", "BIR", "NEW")
            "Espanha" -> listOf("MDR", "BCN", "SEV", "VAL", "BIL")
            "Itália" -> listOf("LOM", "LAZ", "CAM", "TUS", "EMI")
            "Alemanha" -> listOf("BAV", "NRW", "BER", "HAM", "SAX")
            "França" -> listOf("IDF", "PAC", "ARA", "HDF", "NOR")
            else -> return "DIV"
        }
        return list[random.nextInt(list.size)]
    }

    fun getFirstNamesForCountry(country: String): List<String> {
        return when (country) {
            "Brasil" -> listOf("Gabriel", "Lucas", "Pedro", "Marcos", "Bruno", "Matheus", "Rafael", "Diego", "Gustavo", "Felipe", "Thiago", "Vinícius", "Rodrigo", "Eduardo", "Igor", "João", "Arthur", "Guilherme")
            "Argentina", "Espanha", "Colômbia", "Chile", "Uruguai", "Paraguai", "Equador", "Peru", "Bolívia", "Venezuela", "México", "Costa Rica", "Guatemala", "Honduras", "Panamá", "El Salvador", "República Dominicana" -> 
                listOf("Lautaro", "Enzo", "Joaquín", "Mateo", "Thiago", "Valentín", "Bautista", "Nicolás", "Julián", "Gonzalo", "Rodrigo", "Leandro", "Alexis", "Lionel", "Ángel", "Marcos", "Lucas", "Alejandro", "Carlos", "Juan", "Luis", "Mateo", "Santiago")
            "Inglaterra", "Estados Unidos / Canadá", "Jamaica", "Trinidad e Tobago", "Austrália" -> 
                listOf("Harry", "John", "Jack", "Mason", "Marcus", "James", "Charlie", "George", "Oliver", "William", "Jude", "Bukayo", "Declan", "Cole", "Trent", "Phil", "Kyle", "Mason", "Jordan", "Robert", "David", "Michael")
            "Itália" -> listOf("Alessandro", "Lorenzo", "Francesco", "Leonardo", "Andrea", "Mattia", "Gabriele", "Riccardo", "Tommaso", "Davide", "Giuseppe", "Marco", "Antonio", "Federico", "Giovanni")
            "Alemanha", "Áustria", "Suíça" -> listOf("Lukas", "Leon", "Finn", "Jonas", "Luis", "Maximilian", "Felix", "Noah", "Paul", "Elias", "Ben", "Emil", "Thomas", "Stefan", "Andreas", "Michael")
            "França", "Bélgica" -> listOf("Lucas", "Hugo", "Arthur", "Raphael", "Jules", "Leo", "Maël", "Gabriel", "Louis", "Noah", "Kylian", "Antoine", "Pierre", "Jean", "Mathieu")
            "Portugal" -> listOf("João", "Francisco", "Santiago", "Afonso", "Duarte", "Tomás", "Lourenço", "Rodrigo", "Martim", "Miguel", "Diogo", "Tiago", "Rui", "Pedro", "Manuel")
            "Países Baixos" -> listOf("Sem", "Lucas", "Luuk", "Daan", "Milan", "Levi", "Noam", "Finn", "Liam", "Bram", "Thijs", "Sven", "Jan", "Peter")
            "Turquia" -> listOf("Yusuf", "Miraç", "Eymen", "Ömer", "Mustafa", "Ahmet", "Mehmet", "Ali", "Can", "Burak", "Hakan", "Emre", "Arda")
            "Escócia" -> listOf("Callum", "Lewis", "James", "Logan", "Alexander", "Rory", "Brodie", "Finlay", "Andrew", "Scott", "Craig")
            "Dinamarca", "Noruega", "Suécia" -> listOf("Oliver", "William", "Noah", "Emil", "Victor", "Magnus", "Lucas", "Oliver", "Oscar", "Erik", "Karl", "Anders", "Lars", "Sven")
            "Polônia" -> listOf("Antoni", "Jakub", "Jan", "Szymon", "Aleksander", "Franciszek", "Filip", "Mikołaj", "Robert", "Mariusz", "Krzysztof")
            "Tchéquia" -> listOf("Jakub", "Jan", "Tomáš", "Matyáš", "Adam", "Filip", "Vojtěch", "Petr", "Pavel", "Martin")
            "Croácia", "Sérvia" -> listOf("Luka", "Ivan", "Marko", "David", "Filip", "Josip", "Karlo", "Mateo", "Nemanja", "Nikola", "Milan", "Dragan")
            "Grécia" -> listOf("Georgios", "Konstantinos", "Ioannis", "Dimitrios", "Nikolaos", "Panagiotis", "Vasilios", "Christos", "Andreas")
            "Japão" -> listOf("Hiroto", "Ren", "Haruto", "Yuto", "Sota", "Yuma", "Itsuki", "Koki", "Daiki", "Takuya", "Kenji", "Takashi")
            "Coreia do Sul" -> listOf("Min-jun", "Seo-jun", "Ye-jun", "Ji-hoon", "Hyun-woo", "Woo-jin", "Min-jae", "Heung-min", "Sang-woo")
            "Arábia Saudita", "Emirados Árabes Unidos", "Catar", "Irã", "Egito", "Marrocos", "Tunísia" -> 
                listOf("Mohamed", "Ahmed", "Youssef", "Ali", "Hassan", "Salem", "Yasir", "Fahad", "Abdul", "Omar", "Tariq", "Khalid")
            "China" -> listOf("Wei", "Yi", "Hao", "Lei", "Fan", "Bo", "Jun", "Jian", "Ying", "Zhe", "Chen", "Lin")
            "África do Sul" -> listOf("Sipho", "Thabo", "Bongani", "Lungelo", "Kabelo", "Luyanda", "Bandile", "Gift", "Victor", "Ronwen")
            else -> listOf("John", "David", "Michael", "Robert", "James", "William", "Charles", "Thomas")
        }
    }

    fun getLastNamesForCountry(country: String): List<String> {
        return when (country) {
            "Brasil" -> listOf("Silva", "Santos", "Souza", "Oliveira", "Pereira", "Rodrigues", "Almeida", "Nascimento", "Lima", "Araújo", "Costa", "Gomes", "Martins", "Barbosa", "Rocha", "Melo")
            "Argentina", "Espanha", "Colômbia", "Chile", "Uruguai", "Paraguai", "Equador", "Peru", "Bolívia", "Venezuela", "México", "Costa Rica", "Guatemala", "Honduras", "Panamá", "El Salvador", "República Dominicana" -> 
                listOf("Fernández", "Rodríguez", "González", "Martínez", "López", "Gómez", "Díaz", "Álvarez", "Romero", "Sosa", "Medina", "Herrera", "Blanco", "Pérez", "García", "Hernández", "Sanchez", "Ramirez", "Torres", "Flores")
            "Inglaterra", "Estados Unidos / Canadá", "Jamaica", "Trinidad e Tobago", "Austrália" -> 
                listOf("Smith", "Jones", "Taylor", "Williams", "Brown", "Davies", "Evans", "Wilson", "Thomas", "Roberts", "Johnson", "Walker", "Hall", "White", "Green")
            "Itália" -> listOf("Rossi", "Ferrari", "Russo", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca", "Costa")
            "Alemanha", "Áustria", "Suíça" -> listOf("Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Schulz", "Hoffmann", "Bauer", "Richter")
            "França", "Bélgica" -> listOf("Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau", "Simon", "Michel")
            "Portugal" -> listOf("Silva", "Santos", "Ferreira", "Pereira", "Oliveira", "Costa", "Rodrigues", "Martins", "Jesus", "Pinto", "Gomes", "Sousa")
            "Países Baixos" -> listOf("de Jong", "de Vries", "Jansen", "van de Berg", "Bakker", "Smit", "Meijer", "Bos", "Dekker", "Visser")
            "Turquia" -> listOf("Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Yıldırım", "Öztürk", "Aydın", "Özdemir", "Arslan")
            "Escócia" -> listOf("Smith", "MacDonald", "Brown", "Robertson", "Campbell", "Thomson", "Stewart", "Anderson", "Scott", "Murray")
            "Dinamarca", "Noruega", "Suécia" -> listOf("Nielsen", "Jensen", "Hansen", "Pedersen", "Andersen", "Larsen", "Sorensen", "Rasmussen", "Johansen", "Olsen")
            "Polônia" -> listOf("Nowak", "Kowalski", "Wiśniewski", "Wójcik", "Kowalczyk", "Kamiński", "Lewandowski", "Zieliński", "Szymański")
            "Tchéquia" -> listOf("Novák", "Svoboda", "Novotný", "Dvořák", "Černý", "Procházka", "Kučera", "Veselý", "Krejčí")
            "Croácia", "Sérvia" -> listOf("Horvat", "Kovačević", "Babić", "Marić", "Jurić", "Novak", "Kovačić", "Petrović", "Jovanović", "Nikolić")
            "Grécia" -> listOf("Papadopoulos", "Papageorgiou", "Karagiannis", "Vlahos", "Angelopoulos", "Nikolaou", "Dimitriou", "Vasiliou")
            "Japão" -> listOf("Sato", "Suzuki", "Takahashi", "Tanaka", "Watanabe", "Ito", "Yamamoto", "Nakamura", "Kobayashi", "Saito")
            "Coreia do Sul" -> listOf("Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim")
            "Arábia Saudita", "Emirados Árabes Unidos", "Catar", "Irã", "Egito", "Marrocos", "Tunísia" -> 
                listOf("Salah", "Moustafa", "Ali", "Hassan", "Ibrahim", "Mansour", "Gharbi", "Farsi", "Al-Fayegh", "Al-Harbi", "Al-Dawsari")
            "China" -> listOf("Wang", "Li", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Wu", "Zhou")
            "África do Sul" -> listOf("Dlamini", "Ndlovu", "Khumalo", "Mokoena", "Sibanda", "Zulu", "Williams", "Botha", "Pretorius")
            else -> listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson")
        }
    }

    fun generateTeamsForCountry(country: String, sizes: List<Int>, preDefined: List<TeamTemplate>): List<TeamTemplate> {
        val list = mutableListOf<TeamTemplate>()
        val countrySeed = country.hashCode().toLong() and 0x7FFFFFFF
        val random = java.util.Random(countrySeed)
        
        for (divisionIndex in sizes.indices) {
            val div = divisionIndex + 1
            val requestedCount = sizes[divisionIndex]
            val existingInDiv = preDefined.filter { it.division == div }
            
            list.addAll(existingInDiv.take(requestedCount))
            
            val missingCount = requestedCount - existingInDiv.size
            if (missingCount > 0) {
                val cities = countryCities[country] ?: listOf("Capital", "Cidade A", "Cidade B", "Cidade C", "Cidade D")
                val prefixes = listOf("Atlético", "Real", "Deportivo", "Sport", "Inter", "FC", "Club", "União", "Mundial")
                val suffixes = listOf("FC", "City", "United", "Athletic", "Sporting", "AC", "Sociedade")
                
                for (i in 0 until missingCount) {
                    val city = cities[i % cities.size]
                    val prefix = prefixes[random.nextInt(prefixes.size)]
                    val suffix = suffixes[random.nextInt(suffixes.size)]
                    val name = when (random.nextInt(4)) {
                        0 -> "$prefix $city"
                        1 -> "$city $suffix"
                        2 -> city
                        else -> "$city FC"
                    }
                    
                    var uniqueName = name
                    var suffixIndex = 1
                    while (list.any { it.name == uniqueName } || preDefined.any { it.name == uniqueName }) {
                        uniqueName = "$name $suffixIndex"
                        suffixIndex++
                    }
                    
                    val baseRating = when (div) {
                        1 -> 75 + random.nextInt(11)
                        2 -> 67 + random.nextInt(9)
                        3 -> 58 + random.nextInt(9)
                        else -> 45 + random.nextInt(13)
                    }
                    
                    list.add(
                        TeamTemplate(
                            name = uniqueName,
                            city = city,
                            state = countryStateCode(country),
                            division = div,
                            rating = baseRating,
                            stadium = "Arena $city"
                        )
                    )
                }
            }
        }
        return list
    }

    val countriesMap: Map<String, CountryData> by lazy {
        val map = mutableMapOf<String, CountryData>()
        for (country in GlobalFootballSystem.keys) {
            val sizes = countryDivisionSizes[country] ?: listOf(20, 20, 20)
            val cont = GlobalFootballSystem.countries.find { it.name == country }?.confederation ?: "UEFA"
            
            val preDefined = originalMap[country]?.teams ?: emptyList()
            val generatedTeams = generateTeamsForCountry(country, sizes, preDefined)
            
            map[country] = CountryData(
                continent = when (cont) {
                    "UEFA" -> "Europa"
                    "CONMEBOL" -> "América do Sul"
                    "CONCACAF" -> "América do Norte"
                    "CAF" -> "África"
                    "AFC" -> "Ásia"
                    "OFC" -> "Oceania"
                    else -> "Europa"
                },
                firstNames = getFirstNamesForCountry(country),
                lastNames = getLastNamesForCountry(country),
                teams = generatedTeams
            )
        }
        map
    }

    fun getTeamsForCountry(country: String): List<TeamTemplate> {
        return countriesMap[country]?.teams ?: countriesMap["Brasil"]!!.teams
    }

    fun getCountryInfo(country: String): CountryData {
        return countriesMap[country] ?: countriesMap["Brasil"]!!
    }

    private fun Random.safeNextInt(fromInclusive: Int, toInclusive: Int): Int {
        val min = minOf(fromInclusive, toInclusive)
        val max = maxOf(fromInclusive, toInclusive)
        if (min == max) return min
        return nextInt(min, max + 1)
    }

    fun generateRosterForTeam(teamId: Long, teamRating: Int, teamName: String, country: String): List<Player> =
        generateRosterForTeamInternal(teamId, teamRating, teamName, country)

    private fun generateRosterForTeamInternal(
        teamId: Long,
        teamRating: Int,
        teamName: String,
        country: String
    ): List<Player> {
        val list = mutableListOf<Player>()
        val generatedNames = mutableListOf<String>()
        val rand = Random(teamId + teamRating * 17L)

        val positions = listOf(
            "GOL", "GOL", "GOL",
            "ZAG", "ZAG", "ZAG", "ZAG", "ZAG",
            "LAT", "LAT", "LAT", "LAT",
            "VOL", "VOL", "VOL", "VOL", "VOL",
            "MEI", "MEI", "MEI", "MEI",
            "ATA", "ATA", "ATA", "ATA", "ATA", "ATA", "ATA", "ATA", "ATA"
        )

        val countryData = countriesMap[country] ?: countriesMap["Brasil"]!!
        val firstNames = countryData.firstNames
        val lastNames = countryData.lastNames

        val starterLimits = mapOf("GOL" to 1, "ZAG" to 2, "LAT" to 2, "VOL" to 2, "MEI" to 2, "ATA" to 2)
        val currentPosCounts = mutableMapOf<String, Int>()

        for (i in positions.indices) {
            val pos = positions[i]
            val firstName = firstNames[rand.nextInt(firstNames.size)]
            val lastName = lastNames[rand.nextInt(lastNames.size)]
            val name = "$firstName $lastName"
            generatedNames.add(name)

            val force = (teamRating + rand.safeNextInt(-5, 5)).coerceIn(15, 99)
            val age = when (rand.nextDouble()) {
                in 0.0..0.2 -> rand.safeNextInt(17, 20)
                in 0.2..0.7 -> rand.safeNextInt(21, 28)
                else -> rand.safeNextInt(29, 38)
            }

            val contractWeeks = rand.safeNextInt(26, 155)

            val currentCount = currentPosCounts.getOrDefault(pos, 0)
            val isStarter = currentCount < (starterLimits[pos] ?: 0)
            currentPosCounts[pos] = currentCount + 1

            val demandLevels = listOf("low", "medium", "high")
            val demand = demandLevels[rand.nextInt(demandLevels.size)]

            val finishingAttr = when (pos) {
                "ATA" -> rand.safeNextInt(force - 3, force + 4)
                "MEI" -> rand.safeNextInt(force - 8, force + 1)
                "GOL" -> rand.safeNextInt(5, 15)
                else -> rand.safeNextInt(force / 3, force / 2)
            }.coerceIn(10, 99)

            val passingAttr = when (pos) {
                "MEI", "VOL", "LAT" -> rand.safeNextInt(force - 4, force + 3)
                "ATA" -> rand.safeNextInt(force - 10, force)
                "GOL" -> rand.safeNextInt(15, 45)
                else -> rand.safeNextInt(force / 2, force * 2 / 3)
            }.coerceIn(10, 99)

            val paceAttr = when (pos) {
                "ATA", "LAT" -> rand.safeNextInt(force - 2, force + 5)
                "MEI", "ZAG" -> rand.safeNextInt(force - 10, force + 1)
                "GOL" -> rand.safeNextInt(20, 50)
                else -> rand.safeNextInt(force - 8, force + 1)
            }.coerceIn(10, 99)

            val strengthAttr = when (pos) {
                "ZAG", "VOL" -> rand.safeNextInt(force - 2, force + 5)
                "ATA", "GOL" -> rand.safeNextInt(force - 8, force + 1)
                else -> rand.safeNextInt(force - 15, force)
            }.coerceIn(10, 99)

            val visionAttr = when (pos) {
                "MEI" -> rand.safeNextInt(force - 3, force + 4)
                "VOL", "LAT" -> rand.safeNextInt(force - 10, force + 1)
                else -> rand.safeNextInt(15, force - 5)
            }.coerceIn(10, 99)

            val defenseAttr = when (pos) {
                "ZAG", "VOL" -> rand.safeNextInt(force - 3, force + 4)
                "GOL" -> rand.safeNextInt(force - 2, force + 5)
                "LAT" -> rand.safeNextInt(force - 8, force + 1)
                else -> rand.safeNextInt(10, 40)
            }.coerceIn(10, 99)

            val playerId = if (teamId > 0) (teamId * 1000L + (i + 1L)) else 0L
            val moral = rand.safeNextInt(75, 94)
            val careerApps = rand.safeNextInt(0, 150)
            val careerGoals = if (pos == "ATA" || pos == "MEI") rand.safeNextInt(0, 55) else rand.safeNextInt(0, 8)

            val basePlayer = Player(
                id = playerId,
                teamId = teamId,
                name = name,
                age = age,
                nationality = country,
                position = pos,
                force = force,
                energy = 100,
                moral = moral,
                salary = 0L,
                contractDurationWeeks = contractWeeks,
                isFromAcademy = false,
                isStarter = isStarter,
                careerApps = careerApps,
                careerGoals = careerGoals,
                demand_level = demand,
                finishing = finishingAttr,
                passing = passingAttr,
                pace = paceAttr,
                strength = strengthAttr,
                vision = visionAttr,
                defense = defenseAttr
            )

            val mv = basePlayer.calculateMarketValue()
            val minP = (mv * 0.8).toLong().coerceAtLeast(30000L)
            val maxP = (mv * 1.3).toLong().coerceAtLeast(50000L)

            val finalPlayer = basePlayer.copy(
                market_value = mv,
                min_price = minP,
                max_price = maxP,
                salary = basePlayer.calculateSalary(teamRating.toDouble())
            )

            list.add(finalPlayer)
        }
        return list
    }

    fun getLogoForTeam(name: String, country: String): String {
        BrasfootPatchCrests.assetUriFor(country, name)?.let { return it }

        val mappedLogo = when (name) {
            "Flamengo" -> "https://upload.wikimedia.org/wikipedia/commons/2/2e/Flamengo_brazil_logo.svg"
            "Palmeiras" -> "https://upload.wikimedia.org/wikipedia/commons/1/10/Palmeiras_logo.svg"
            "Atlético Mineiro" -> "https://upload.wikimedia.org/wikipedia/commons/2/2f/Clube_Atl%C3%A9tico_Mineiro_logo.svg"
            "Cruzeiro" -> "https://upload.wikimedia.org/wikipedia/commons/b/b3/Cruzeiro_Esporte_Clube_logo.svg"
            "São Paulo" -> "https://upload.wikimedia.org/wikipedia/commons/6/6f/Brasao_do_Sao_Paulo_Futebol_Clube.svg"
            "Fluminense" -> "https://upload.wikimedia.org/wikipedia/commons/a/ad/Fluminense_FC_logo.svg"
            "Grêmio" -> "https://upload.wikimedia.org/wikipedia/commons/f/f1/Gremio_logo.svg"
            "Internacional" -> "https://upload.wikimedia.org/wikipedia/commons/3/35/Sport_Club_Internacional_logo.svg"
            "Botafogo" -> "https://upload.wikimedia.org/wikipedia/commons/c/cb/Botafogo_de_Futebol_e_Regatas_logo.svg"
            "Corinthians" -> "https://upload.wikimedia.org/wikipedia/commons/5/5a/Sport_Club_Corinthians_Paulista_logo.svg"
            "Vasco da Gama" -> "https://upload.wikimedia.org/wikipedia/commons/a/a4/Vasco_da_Gama_logo.svg"
            "Santos" -> "https://upload.wikimedia.org/wikipedia/commons/3/35/Santos_FC_logo.svg"
            "Bahia" -> "https://upload.wikimedia.org/wikipedia/commons/9/90/Esporte_Clube_Bahia_logo.svg"
            "Fortaleza" -> "https://upload.wikimedia.org/wikipedia/commons/0/04/Fortaleza_Esporte_Clube_logo.svg"
            "América Mineiro" -> "https://upload.wikimedia.org/wikipedia/commons/a/a8/America_Futebol_Clube_MG_logo.svg"
            "Sport Recife" -> "https://upload.wikimedia.org/wikipedia/commons/7/7b/Sport_Club_do_Recife_logo.svg"
            "Coritiba" -> "https://upload.wikimedia.org/wikipedia/commons/8/8e/Coritiba_FBC_logo.svg"
            "Goiás" -> "https://upload.wikimedia.org/wikipedia/commons/7/7b/Goias_Esporte_Clube_logo.svg"
            "Ceará" -> "https://upload.wikimedia.org/wikipedia/commons/4/4c/Ceara_Futebol_Clube_logo.svg"
            "Novorizontino" -> "https://upload.wikimedia.org/wikipedia/commons/1/1a/Gremio_Novorizontino_logo.png"
            "Chapecoense" -> "https://upload.wikimedia.org/wikipedia/commons/6/6e/Associa%C3%A7%C3%A3o_Chapecoense_de_Futebol_logo.svg"
            "Athletic Club" -> "https://upload.wikimedia.org/wikipedia/commons/0/04/Athletic_Club_de_Sao_Joao_del-Rei.png"

            "Manchester City" -> "https://upload.wikimedia.org/wikipedia/en/e/eb/Manchester_City_FC_badge.svg"
            "Arsenal FC" -> "https://upload.wikimedia.org/wikipedia/en/5/53/Arsenal_FC.svg"
            "Liverpool FC" -> "https://upload.wikimedia.org/wikipedia/en/0/0c/Liverpool_FC.svg"
            "Chelsea FC" -> "https://upload.wikimedia.org/wikipedia/en/c/cc/Chelsea_FC.svg"
            "Manchester United" -> "https://upload.wikimedia.org/wikipedia/en/7/7a/Manchester_United_FC_crest.svg"
            "Tottenham Hotspur" -> "https://upload.wikimedia.org/wikipedia/en/b/b4/Tottenham_Hotspur.svg"
            "Aston Villa" -> "https://upload.wikimedia.org/wikipedia/en/f/f9/Aston_Villa_FC_crest_%282016%29.svg"
            "Newcastle United" -> "https://upload.wikimedia.org/wikipedia/en/5/56/Newcastle_United_Logo.svg"
            "West Ham United" -> "https://upload.wikimedia.org/wikipedia/en/c/c2/West_Ham_United_FC_logo.svg"
            "Everton FC" -> "https://upload.wikimedia.org/wikipedia/en/7/7c/Everton_FC_logo.svg"
            "Leicester City" -> "https://upload.wikimedia.org/wikipedia/en/2/2d/Leicester_City_crest.svg"
            "Leeds United" -> "https://upload.wikimedia.org/wikipedia/en/5/54/Leeds_United_F.C._logo.svg"
            "Wrexham AFC" -> "https://upload.wikimedia.org/wikipedia/en/a/ae/Wrexham_AFC_crest.svg"

            "Real Madrid" -> "https://upload.wikimedia.org/wikipedia/en/5/56/Real_Madrid_CF.svg"
            "Barcelona" -> "https://upload.wikimedia.org/wikipedia/en/4/47/FC_Barcelona_%28crest%29.svg"
            "Atlético de Madrid" -> "https://upload.wikimedia.org/wikipedia/en/f/f4/Atletico_Madrid_2017_logo.svg"
            "Girona" -> "https://upload.wikimedia.org/wikipedia/en/b/b4/Girona_FC_logo.svg"
            "Real Sociedad" -> "https://upload.wikimedia.org/wikipedia/en/f/f1/Real_Sociedad_logo.svg"
            "Athletic Bilbao" -> "https://upload.wikimedia.org/wikipedia/en/9/98/Club_Athletic_Bilbao_logo.svg"
            "Real Betis" -> "https://upload.wikimedia.org/wikipedia/en/1/13/Real_betis_logo.svg"
            "Villarreal" -> "https://upload.wikimedia.org/wikipedia/en/7/70/Villarreal_CF_logo.svg"
            "Sevilla FC" -> "https://upload.wikimedia.org/wikipedia/en/3/3b/Sevilla_FC_logo.svg"
            "Valencia CF" -> "https://upload.wikimedia.org/wikipedia/en/7/75/Valencia_CF_logo.svg"

            "River Plate" -> "https://upload.wikimedia.org/wikipedia/commons/a/ac/Escudo_do_Club_Atl%C3%A9tico_River_Plate.svg"
            "Boca Juniors" -> "https://upload.wikimedia.org/wikipedia/commons/1/14/Escudo_do_Club_Atl%C3%A9tico_Boca_Juniors.svg"
            "Racing Club" -> "https://upload.wikimedia.org/wikipedia/commons/5/5f/Escudo_de_Racing_Club.svg"
            "Independiente" -> "https://upload.wikimedia.org/wikipedia/commons/d/db/Escudo_Club_Atl%C3%A9tico_Independiente.svg"
            "San Lorenzo" -> "https://upload.wikimedia.org/wikipedia/commons/7/7b/Escudo_de_San_Lorenzo_de_Almagro.svg"
            "Estudiantes LP" -> "https://upload.wikimedia.org/wikipedia/commons/7/7a/Escudo_de_Estudiantes_de_La_Plata.svg"
            "Vélez Sarsfield" -> "https://upload.wikimedia.org/wikipedia/commons/a/a1/Escudo_del_Club_Atl%C3%A9tico_V%C3%A9lez_Sarsfield.svg"
            "Talleres Córdoba" -> "https://upload.wikimedia.org/wikipedia/commons/d/d3/Escudo_Talleres_de_Cordoba.svg"

            "Inter Miami CF" -> "https://upload.wikimedia.org/wikipedia/en/1/1c/Inter_Miami_CF_logo.svg"
            "Columbus Crew" -> "https://upload.wikimedia.org/wikipedia/en/0/0a/Columbus_Crew_logo.svg"
            "LAFC" -> "https://upload.wikimedia.org/wikipedia/en/3/3d/Los_Angeles_FC_logo.svg"
            "LA Galaxy" -> "https://upload.wikimedia.org/wikipedia/en/1/1a/LA_Galaxy_logo.svg"
            "Club América" -> "https://upload.wikimedia.org/wikipedia/en/3/32/Club_Am%C3%A9rica_logo.svg"
            "Cruz Azul" -> "https://upload.wikimedia.org/wikipedia/commons/e/ec/Cruz_azul_shield.svg"
            "Tigres UANL" -> "https://upload.wikimedia.org/wikipedia/en/e/e6/Tigres_UANL_logo.svg"
            "CF Monterrey" -> "https://upload.wikimedia.org/wikipedia/en/c/ca/CF_Monterrey_logo.svg"
            "Chivas Guadalajara" -> "https://upload.wikimedia.org/wikipedia/commons/0/07/Club_Deportivo_Guadalajara.png"

            "Al Ahly SC" -> "https://upload.wikimedia.org/wikipedia/commons/b/b9/Al_Ahly_New_Logo.svg"
            "Mamelodi Sundowns" -> "https://upload.wikimedia.org/wikipedia/en/1/13/Mamelodi_Sundowns_logo.svg"
            "Wydad AC" -> "https://upload.wikimedia.org/wikipedia/en/c/cb/Wydad_Athletic_Club_logo.svg"
            "Raja CA" -> "https://upload.wikimedia.org/wikipedia/en/9/9f/Raja_Club_Athletic_logo.svg"
            "Zamalek SC" -> "https://upload.wikimedia.org/wikipedia/en/7/7b/ZamalekSC.svg"
            "TP Mazembe" -> "https://upload.wikimedia.org/wikipedia/en/d/da/TP_Mazembe_logo.svg"
            "Al Hilal SFC" -> "https://upload.wikimedia.org/wikipedia/en/f/f9/Al-Hilal_LFC_logo.svg"
            "Al Nassr FC" -> "https://upload.wikimedia.org/wikipedia/en/3/30/Al_Nassr_Logo.svg"
            "Al Ittihad Club" -> "https://upload.wikimedia.org/wikipedia/en/3/3e/Al_Ittihad_Jeddah_club_logo.svg"
            "Al Ahli Saudi" -> "https://upload.wikimedia.org/wikipedia/en/b/b4/Al-Ahli_Saudi_FC_logo.svg"
            "Sydney FC" -> "https://upload.wikimedia.org/wikipedia/en/a/ad/Sydney_FC_logo_%282017%29.svg"
            "Melbourne City" -> "https://upload.wikimedia.org/wikipedia/en/d/de/Melbourne_City_FC_logo.svg"
            "Auckland City FC" -> "https://upload.wikimedia.org/wikipedia/en/f/f3/Auckland_City_FC.svg"
            
            else -> null
        }

        if (mappedLogo != null) return mappedLogo

        val encodedSeed = try {
            java.net.URLEncoder.encode(name, "UTF-8")
        } catch (e: Exception) {
            name.replace(" ", "%20")
        }
        return "https://api.dicebear.com/7.x/initials/svg?seed=$encodedSeed&radius=10&fontSize=42&chars=2"
    }

    fun getCompetitionName(competitionType: String, country: String): String {
        var mappedType = when (competitionType) {
            "CONTINENTAL" -> "CONTINENTAL_T1"
            else -> competitionType
        }
        if (mappedType.startsWith("CONTINENTAL_T1_GP_")) {
            mappedType = "CONTINENTAL_T1"
        } else if (mappedType.startsWith("CONTINENTAL_T2_GP_")) {
            mappedType = "CONTINENTAL_T2"
        } else if (mappedType.startsWith("WORLD_CUP_GP_")) {
            mappedType = "WORLD_CUP"
        }
        return when (country) {
            "Inglaterra" -> when (mappedType) {
                "ESTADUAL" -> "EFL Carabao Cup 🏴"
                "COPA" -> "FA Cup 🏆"
                "SERIE_A" -> "Premier League 🦁"
                "SERIE_B" -> "Championship 🏴"
                "SERIE_C" -> "League One 🏴"
                "SERIE_D" -> "League Two 🏴"
                "CONTINENTAL_T1" -> "UEFA Champions League 🇪🇺"
                "CONTINENTAL_T2" -> "UEFA Europa League 🇪🇺"
                "CONTINENTAL_T3" -> "UEFA Conference League 🇪🇺"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "Espanha" -> when (mappedType) {
                "ESTADUAL" -> "Copa Catalunya 🏆"
                "COPA" -> "Copa del Rey 🇪🇸"
                "SERIE_A" -> "La Liga 🇪🇸"
                "SERIE_B" -> "La Liga 2 🇪🇸"
                "SERIE_C" -> "1ª RFEF 🇪🇸"
                "SERIE_D" -> "2ª RFEF 🇪🇸"
                "CONTINENTAL_T1" -> "UEFA Champions League 🇪🇺"
                "CONTINENTAL_T2" -> "UEFA Europa League 🇪🇺"
                "CONTINENTAL_T3" -> "UEFA Conference League 🇪🇺"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "Argentina" -> when (mappedType) {
                "ESTADUAL" -> "Copa de la Liga 🇦🇷"
                "COPA" -> "Copa Argentina 🏆"
                "SERIE_A" -> "Liga Profesional 🇦🇷"
                "SERIE_B" -> "Primera Nacional 🇦🇷"
                "SERIE_C" -> "Primera B Metro 🇦🇷"
                "SERIE_D" -> "Federal A 🇦🇷"
                "CONTINENTAL_T1" -> "Copa Libertadores 🏆"
                "CONTINENTAL_T2" -> "Copa Sudamericana 🥈"
                "CONTINENTAL_T3" -> "Copa Libertadores"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "Estados Unidos / México" -> when (mappedType) {
                "ESTADUAL" -> "Campeonato Norte 🇺🇸🇲🇽"
                "COPA" -> "Leagues Cup 🏆"
                "SERIE_A" -> "Liga MX Elite 🇲🇽"
                "SERIE_B" -> "MLS Division 🇺🇸"
                "SERIE_C" -> "USL Championship 🇺🇸"
                "SERIE_D" -> "USL League One 🇺🇸"
                "CONTINENTAL_T1" -> "CONCACAF Champions Cup 🏆"
                "CONTINENTAL_T2" -> "CONCACAF Central American Cup 🌎"
                "CONTINENTAL_T3" -> "CONCACAF Caribbean Cup 🏝️"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "América Central" -> when (mappedType) {
                "ESTADUAL" -> "Copa Centro 🏆"
                "COPA" -> "Copa de Campeones"
                "SERIE_A" -> "División 1"
                "SERIE_B" -> "División 2"
                "SERIE_C" -> "División 3"
                "SERIE_D" -> "División 4"
                "CONTINENTAL_T1" -> "CONCACAF Champions Cup 🏆"
                "CONTINENTAL_T2" -> "CONCACAF Central American Cup 🌎"
                "CONTINENTAL_T3" -> "CONCACAF Caribbean Cup 🏝️"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "África" -> when (mappedType) {
                "ESTADUAL" -> "Copa CAF 🏆"
                "COPA" -> "Copa das Nações 🏆"
                "SERIE_A" -> "CAF Champions League 1"
                "SERIE_B" -> "CAF Champions League 2"
                "SERIE_C" -> "Confederation Cup"
                "SERIE_D" -> "Regional"
                "CONTINENTAL_T1" -> "CAF Champions League 🏆"
                "CONTINENTAL_T2" -> "CAF Confederation Cup 🥈"
                "CONTINENTAL_T3" -> "CAF Champions League"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "Ásia" -> when (mappedType) {
                "ESTADUAL" -> "AFC Cup 🏆"
                "COPA" -> "Copa do Rei Saudita"
                "SERIE_A" -> "Saudi Pro League 🇸🇦"
                "SERIE_B" -> "Saudi Division 1 🇸🇦"
                "SERIE_C" -> "J-League / K-League 🇯🇵🇰🇷"
                "SERIE_D" -> "Regional"
                "CONTINENTAL_T1" -> "AFC Champions League Elite 🏆"
                "CONTINENTAL_T2" -> "AFC Champions League Two 🥈"
                "CONTINENTAL_T3" -> "AFC Challenge League 🥉"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "Oceania" -> when (mappedType) {
                "ESTADUAL" -> "OFC Champions 🏆"
                "COPA" -> "Australia Cup"
                "SERIE_A" -> "A-League Elite 🇦🇺"
                "SERIE_B" -> "NZ National League 🇳🇿"
                "SERIE_C" -> "Regional A"
                "SERIE_D" -> "Regional B"
                "CONTINENTAL_T1" -> "OFC Champions League 🏆"
                "CONTINENTAL_T2" -> "OFC Champions League"
                "CONTINENTAL_T3" -> "OFC Champions League"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            "África / Ásia / Oceania" -> when (mappedType) {
                "ESTADUAL" -> "Copa Intercontinental"
                "COPA" -> "Copa Afro-Asiática"
                "SERIE_A" -> "Superliga Mundial"
                "SERIE_B" -> "Superliga B"
                "SERIE_C" -> "Divisão C"
                "SERIE_D" -> "Divisão D"
                "CONTINENTAL_T1" -> "Copa Mundial de Clubes 🌍"
                "CONTINENTAL_T2" -> "Copa Mundial B"
                "CONTINENTAL_T3" -> "Copa Mundial C"
                "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                else -> mappedType
            }
            else -> {
                if (country == "Brasil") {
                    when (mappedType) {
                        "ESTADUAL" -> "Estadual MG 🔺"
                        "COPA" -> "Copa do Brasil 🏆"
                        "SERIE_A" -> "Brasileirão Série A 🇧🇷"
                        "SERIE_B" -> "Brasileirão Série B 🇧🇷"
                        "SERIE_C" -> "Brasileirão Série C 🇧🇷"
                        "SERIE_D" -> "Brasileirão Série D 🇧🇷"
                        "CONTINENTAL_T1" -> "Copa Libertadores 🏆"
                        "CONTINENTAL_T2" -> "Copa Sudamericana 🥈"
                        "CONTINENTAL_T3" -> "Copa Libertadores"
                        "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                        else -> mappedType
                    }
                } else {
                    val cont = GlobalFootballSystem.countries.find { it.name == country }?.confederation ?: "UEFA"
                    when (mappedType) {
                        "SERIE_A" -> "$country - 1ª Divisão 🏆"
                        "SERIE_B" -> "$country - 2ª Divisão 🥈"
                        "SERIE_C" -> "$country - 3ª Divisão"
                        "SERIE_D" -> "$country - 4ª Divisão"
                        "COPA" -> "Taça de $country 🏆"
                        "ESTADUAL" -> "Copa Local de $country 📍"
                        "WORLD_CUP" -> "FIFA Club World Cup 🌍"
                        "CONTINENTAL_T1" -> when (cont) {
                            "UEFA" -> "UEFA Champions League 🇪🇺"
                            "CONMEBOL" -> "Copa Libertadores 🏆"
                            "CONCACAF" -> "CONCACAF Champions Cup 🏆"
                            "CAF" -> "CAF Champions League 🏆"
                            "AFC" -> "AFC Champions League Elite 🏆"
                            else -> "Champions League 🏆"
                        }
                        "CONTINENTAL_T2" -> when (cont) {
                            "UEFA" -> "UEFA Europa League 🇪🇺"
                            "CONMEBOL" -> "Copa Sudamericana 🥈"
                            "CONCACAF" -> "CONCACAF Central American Cup 🌎"
                            "CAF" -> "CAF Confederation Cup 🥈"
                            "AFC" -> "AFC Champions League Two 🥈"
                            else -> "Europa League 🥈"
                        }
                        "CONTINENTAL_T3" -> when (cont) {
                            "UEFA" -> "UEFA Conference League 🇪🇺"
                            "CONMEBOL" -> "Copa Sudamericana"
                            "CONCACAF" -> "CONCACAF Caribbean Cup 🏝️"
                            "CAF" -> "CAF Confederation Cup"
                            "AFC" -> "AFC Challenge League 🥉"
                            else -> "Conference League 🥉"
                        }
                        else -> mappedType
                    }
                }
            }
        }
    }

    fun getStadiumCapacityForTeam(name: String, rating: Int): Int {
        return when (name) {
            "Barcelona", "FC Barcelona" -> 99354
            "Real Madrid" -> 85000
            "Atlético de Madrid" -> 70460
            "Real Betis" -> 60721
            "Athletic Bilbao" -> 53289
            "Valencia CF" -> 49430
            "Sevilla FC" -> 43883
            "Real Sociedad" -> 39500
            "Villarreal" -> 23000
            "Girona" -> 14624
            "Espanyol" -> 40000
            "Real Zaragoza" -> 33608
            "Real Valladolid" -> 27618
            "Levante UD" -> 26354
            "Elche CF" -> 33732
            "Tenerife" -> 22824
            "Real Oviedo" -> 30500
            "Leganés" -> 12450
            "Sporting Gijón" -> 30000
            "Eibar" -> 8164

            "Flamengo", "Fluminense" -> 78838
            "Palmeiras" -> 43713
            "Atlético Mineiro" -> 46000
            "Cruzeiro" -> 61846
            "São Paulo" -> 66795
            "Grêmio" -> 55662
            "Internacional" -> 50842
            "Botafogo" -> 44661
            "Corinthians" -> 49205
            "Vasco da Gama" -> 21880
            "Santos" -> 16068
            "Bahia" -> 48000
            "Fortaleza", "Ceará" -> 63903
            "América Mineiro" -> 23016
            "Sport Recife" -> 26418
            "Coritiba" -> 40502
            "Goiás" -> 14525
            "Novorizontino" -> 14000

            "Manchester City" -> 53400
            "Arsenal FC" -> 60704
            "Liverpool FC" -> 61276
            "Chelsea FC" -> 40341
            "Manchester United" -> 74310
            "Tottenham Hotspur" -> 62850
            "Aston Villa" -> 42640
            "Newcastle United" -> 52305
            "West Ham United" -> 62500
            "Everton FC" -> 39572
            "Leicester City" -> 32261
            "Leeds United" -> 37792
            "Southampton FC" -> 32384
            "Norwich City" -> 27244
            "Sunderland AFC" -> 49000
            "Middlesbrough" -> 34742
            "Ipswich Town" -> 29673
            "West Bromwich" -> 26688
            "Coventry City" -> 32609
            "Hull City" -> 25400

            "Boca Juniors" -> 54000
            "River Plate" -> 84567
            "Racing Club" -> 51389
            "Independiente" -> 42069
            "San Lorenzo" -> 47964
            "Estudiantes LP" -> 32530
            "Vélez Sarsfield" -> 49540
            "Talleres Córdoba" -> 57000

            else -> 10000 + rating * 250
        }
    }

    fun getCountryForTeam(name: String): String {
        for ((country, data) in countriesMap) {
            if (data.teams.any { it.name == name }) {
                return country
            }
        }
        return "Brasil"
    }

    val copaDoBrasilParticipants = listOf(
        "Galvez", "Vasco-AC", "Independência",
        "Penedense", "ASA", "CRB", "CSA",
        "Oratório", "Independente", "Trem",
        "Amazonas", "Nacional", "Manaus", "Manauara",
        "Porto", "Atlético-BA", "Jacuipense", "Juazeirense", "Bahia", "Vitória",
        "Tirol", "Ceará", "Fortaleza", "Maracanã",
        "Brasiliense", "Capital", "Gama",
        "Desportiva Ferroviária", "Porto Vitória", "Rio Branco-ES",
        "Anápolis", "Atlético-GO", "Goiás", "Vila Nova",
        "Iape", "Imperatriz", "Maranhão",
        "Primavera", "Cuiabá", "Mixto", "Operário VG",
        "Ivinhema", "Pantanal", "Operário-MS",
        "Betim", "América-MG", "Athletic Club", "Tombense", "Uberlândia", "Atlético-MG", "Cruzeiro",
        "Bragantino-PA", "Águia de Marabá", "Castanhal", "Tuna Luso", "Paysandu", "Remo",
        "Serra Branca", "Botafogo-PB", "Sousa",
        "Azuriz", "Cianorte", "Londrina", "Maringá", "Operário-PR", "Athletico-PR", "Coritiba",
        "Maguary", "Retrô", "Santa Cruz", "Sport",
        "Piauí", "Altos", "Fluminense-PI",
        "Sampaio Corrêa-RJ", "Boavista", "Madureira", "Nova Iguaçu", "Portuguesa-RJ", "Volta Redonda", "Botafogo", "Flamengo", "Fluminense", "Vasco",
        "Laguna", "ABC", "América-RN",
        "Guarany de Bagé", "Caxias", "Juventude", "São Luiz", "Ypiranga-RS", "Grêmio", "Internacional",
        "Guaporé", "Ji-Paraná", "Porto Velho",
        "Baré", "Monte Roraima", "GAS",
        "Santa Catarina", "Avaí", "Figueirense", "Joinville", "Barra-SC", "Chapecoense",
        "Primavera-SP", "Velo Clube", "Guarani", "Novorizontino", "Portuguesa", "São Bernardo", "Ponte Preta", "Bragantino", "Corinthians", "Mirassol", "Palmeiras", "Santos", "São Paulo",
        "América de Propriá", "Itabaiana", "Lagarto", "Confiança",
        "Araguaína", "Gurupi", "Tocantinópolis"
    )
}
