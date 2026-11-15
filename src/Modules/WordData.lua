-- Curated semantic vocabulary (server only, never replicated to clients).
-- Category > Subcategory > word = feature tags. Similarity is computed from these.
local DATA = {
	Animals = {
		Pets = {
			dog = "furry loyal bark domestic tail", cat = "furry purr domestic claws tail",
			hamster = "furry small domestic cage", rabbit = "furry small hop carrot",
			goldfish = "swim water small domestic", turtle = "shell slow swim reptile",
		},
		Farm = {
			horse = "furry fast ride farm", cow = "furry farm milk big", pig = "pink farm mud",
			sheep = "furry farm wool", chicken = "feathers farm eggs", goat = "furry farm horns",
		},
		Wild = {
			lion = "furry roar savanna predator", tiger = "stripes roar jungle predator",
			elephant = "trunk big savanna gray", giraffe = "tall spots savanna neck",
			wolf = "furry howl predator forest", bear = "furry big forest hibernate",
			monkey = "jungle climb banana tail", zebra = "stripes savanna horse",
			kangaroo = "hop pouch australia", panda = "furry bamboo black white",
		},
		Sea = {
			shark = "swim ocean predator teeth", whale = "swim ocean big blue",
			dolphin = "swim ocean smart friendly", octopus = "ocean tentacles smart",
			crab = "ocean claws shell beach", jellyfish = "ocean swim sting", seahorse = "ocean small swim",
		},
		Birds = {
			eagle = "fly feathers predator mountain", owl = "fly feathers night wise",
			penguin = "ice swim cold feathers", parrot = "fly feathers colorful talk",
			duck = "fly feathers water quack", flamingo = "feathers pink water tall",
		},
		Insects = {
			bee = "fly sting yellow honey", butterfly = "fly colorful wings garden",
			ant = "small tiny colony", spider = "web legs spooky", ladybug = "small red garden spots",
		},
	},
	Food = {
		Fruit = {
			apple = "sweet red tree round", banana = "sweet yellow tropical peel",
			orange = "sweet citrus round juice", grape = "sweet small vine juice",
			lemon = "sour citrus yellow juice", strawberry = "sweet red small seeds",
			watermelon = "sweet green summer juice", pineapple = "sweet tropical spiky yellow",
			cherry = "sweet red small tree", mango = "sweet tropical yellow juice",
		},
		Veggies = {
			carrot = "orange crunchy garden healthy", potato = "brown underground fried",
			tomato = "red juicy garden", broccoli = "green healthy tree",
			corn = "yellow farm crunchy", onion = "layers garden cry",
		},
		Meals = {
			pizza = "cheese baked italian hot", burger = "meat bun fastfood hot",
			pasta = "italian noodles sauce hot", sushi = "fish rice japanese raw",
			taco = "mexican shell spicy", salad = "healthy cold green",
			sandwich = "bread lunch cold", soup = "hot liquid bowl",
		},
		Sweets = {
			cake = "sweet birthday baked", cookie = "sweet baked small",
			chocolate = "sweet brown candy", icecream = "sweet cold summer frozen",
			donut = "sweet fried round", candy = "sweet colorful small", pie = "sweet baked fruit",
		},
		Drinks = {
			coffee = "hot brown caffeine morning", tea = "hot leaves morning",
			milk = "white cold dairy", juice = "sweet fruit cold", water = "clear cold liquid",
			soda = "sweet fizzy cold", lemonade = "sour sweet summer cold",
		},
	},
	Nature = {
		Weather = {
			rain = "water cloud wet cold", snow = "cold white winter ice", sun = "hot bright summer sky",
			wind = "air fast sky", storm = "water cloud loud dark", cloud = "white sky water",
			rainbow = "colorful sky water", lightning = "storm bright fast sky", fog = "white cloud cold",
		},
		Landscape = {
			mountain = "tall rock snow high", ocean = "water blue big waves", forest = "trees green animals",
			desert = "hot sand dry", river = "water flow fresh", volcano = "hot lava mountain",
			island = "water beach tropical", cave = "dark rock underground", beach = "sand water summer",
			waterfall = "water river tall",
		},
		Plants = {
			tree = "wood green tall leaves", flower = "colorful petals garden smell",
			grass = "green lawn small", cactus = "desert spiky green", rose = "flower red thorns",
			mushroom = "forest small fungus", leaf = "green tree small",
		},
	},
	Space = {
		Cosmos = {
			moon = "night sky bright round", star = "night sky bright far", planet = "round space orbit",
			rocket = "space fast fly fire", comet = "space ice fast", galaxy = "space star big",
			alien = "space green strange", astronaut = "space suit rocket", telescope = "lens sky far",
		},
	},
	Objects = {
		Vehicles = {
			car = "wheels road fast engine", bike = "wheels road pedal", train = "wheels track long fast",
			airplane = "fly sky fast wings", ship = "water ocean big", bus = "wheels road big",
			helicopter = "fly sky blades", boat = "water small sail", skateboard = "wheels small fun",
			motorcycle = "wheels fast engine road",
		},
		Tech = {
			phone = "screen call pocket", computer = "screen keyboard work", robot = "metal machine smart",
			camera = "photo lens picture", television = "screen watch show", headphones = "music ear sound",
			keyboard = "keys type computer", battery = "power small energy", lightbulb = "light glass energy",
		},
		Home = {
			chair = "sit wood furniture", table = "wood furniture flat", bed = "sleep soft furniture",
			lamp = "light furniture night", door = "wood open house", window = "glass house view",
			mirror = "glass reflect", clock = "time round wall", couch = "sit soft furniture",
			fridge = "cold kitchen food", oven = "hot kitchen baked",
		},
		Tools = {
			hammer = "metal build hit", knife = "metal sharp kitchen", scissors = "metal sharp cut",
			key = "metal small door", pencil = "write wood small", book = "read paper pages",
			axe = "metal sharp wood", ladder = "climb tall wood", umbrella = "rain cover wet",
		},
		Clothing = {
			shirt = "wear cotton", shoes = "wear feet walk", hat = "wear head sun",
			jacket = "wear warm cold", socks = "wear feet warm", glasses = "wear eyes glass",
			gloves = "wear hands warm", scarf = "wear warm cold neck",
		},
	},
	Culture = {
		Sports = {
			soccer = "ball team field kick", basketball = "ball team court hoop", tennis = "ball racket court",
			swimming = "water sport pool", baseball = "ball bat team", golf = "ball club grass",
			boxing = "fight gloves ring", skiing = "snow cold mountain", bowling = "ball pins indoor",
		},
		Music = {
			guitar = "strings music rock", piano = "keys music classical", drums = "beat music rock loud",
			violin = "strings music classical", trumpet = "brass music loud jazz",
			song = "music voice sing", microphone = "sound voice sing",
		},
		Fantasy = {
			dragon = "fire fly wings legend", wizard = "magic wand hat", castle = "stone king big",
			pirate = "ship treasure sea", ghost = "spooky night white", unicorn = "horse horn magic",
			vampire = "night spooky blood", treasure = "gold chest pirate", crown = "gold king head",
			sword = "metal sharp fight",
		},
	},
	People = {
		Body = {
			hand = "fingers touch", heart = "beat love red", eye = "see face",
			brain = "think smart head", tooth = "white mouth bite", nose = "smell face",
		},
		Jobs = {
			doctor = "hospital help medicine", teacher = "school help books", chef = "kitchen food cook",
			farmer = "farm food outdoors", pilot = "fly sky plane", police = "help law uniform",
			firefighter = "fire help uniform", artist = "paint colorful creative",
		},
	},
	Places = {
		Buildings = {
			school = "learn books teacher", hospital = "doctor help medicine", library = "books quiet read",
			airport = "plane fly travel", museum = "art history quiet", bridge = "river cross long",
			tower = "tall high view", house = "home roof door", farmhouse = "farm home barn",
		},
	},
}

local info, list = {}, {}
for cat, subs in pairs(DATA) do
	for sub, words in pairs(subs) do
		for word, feats in pairs(words) do
			local set, n = {}, 0
			for f in feats:gmatch("%a+") do
				if not set[f] then
					set[f] = true
					n += 1
				end
			end
			info[word] = { word = word, cat = cat, sub = sub, feats = set, n = n }
			table.insert(list, word)
		end
	end
end
table.sort(list)

return { info = info, list = list }
