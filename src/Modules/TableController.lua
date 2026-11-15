-- One TableController per table model. Owns seating, match state, scoring and
-- all validation. The secret word only ever lives here (and is sent to the Setter).
local Players = game:GetService("Players")
local TweenService = game:GetService("TweenService")

local CFG = {
	START_DELAY = 3,
	CHOOSE_TIME = 30,
	GUESS_TIME = 120,
	RESULT_TIME = 20,
	GUESS_COOLDOWN = 0.6,
	MAX_LEN = 20,
}

local COLORS = {
	Empty = Color3.fromRGB(80, 230, 120),
	Waiting = Color3.fromRGB(255, 200, 60),
	Busy = Color3.fromRGB(255, 90, 90),
}

local TableController = {}
TableController.__index = TableController

local function normalize(raw)
	if type(raw) ~= "string" then return nil end
	local w = raw:lower():gsub("[^a-z]", "")
	if #w == 0 or #w > CFG.MAX_LEN then return nil end
	return w
end

local function addStat(player, name, amount)
	local ls = player:FindFirstChild("leaderstats")
	local v = ls and ls:FindFirstChild(name)
	if v then v.Value += amount end
end

function TableController.new(model, deps)
	local self = setmetatable({}, TableController)
	self.model = model
	self.name = model.Name
	self.deps = deps
	self.seats = { model:WaitForChild("Seat1"), model:WaitForChild("Seat2") }
	self.statusPart = model:WaitForChild("Status")
	self.light = model:WaitForChild("Light")
	self.phase = "Empty"
	self.players = {}
	self.occupants = {}
	self.scores = {}
	self.votes = {}
	self.history = {}
	self.lastGuess = {}
	self.round = 0
	self.token = 0
	self.setterIndex = 1
	self.endsAt = 0

	self:_buildBillboard()
	for _, seat in ipairs(self.seats) do
		seat:GetPropertyChangedSignal("Occupant"):Connect(function()
			self:_onOccupant(seat)
		end)
	end
	self:_refreshStatus()
	return self
end

-- ===== status board =====
function TableController:_buildBillboard()
	local gui = Instance.new("BillboardGui")
	gui.Name = "StatusGui"
	gui.Size = UDim2.fromOffset(300, 90)
	gui.MaxDistance = 100
	gui.Adornee = self.statusPart
	gui.Parent = self.statusPart

	local card = Instance.new("Frame")
	card.Size = UDim2.fromScale(1, 1)
	card.BackgroundColor3 = Color3.fromRGB(24, 26, 40)
	card.BackgroundTransparency = 0.1
	card.Parent = gui
	local corner = Instance.new("UICorner")
	corner.CornerRadius = UDim.new(0, 16)
	corner.Parent = card
	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.fromRGB(122, 102, 255)
	stroke.Thickness = 2
	stroke.Parent = card

	self.title = Instance.new("TextLabel")
	self.title.BackgroundTransparency = 1
	self.title.Size = UDim2.new(1, -16, 0.55, 0)
	self.title.Position = UDim2.fromOffset(8, 6)
	self.title.Font = Enum.Font.GothamBold
	self.title.TextScaled = true
	self.title.Parent = card

	self.sub = Instance.new("TextLabel")
	self.sub.BackgroundTransparency = 1
	self.sub.Size = UDim2.new(1, -16, 0.3, 0)
	self.sub.Position = UDim2.new(0, 8, 0.62, 0)
	self.sub.Font = Enum.Font.Gotham
	self.sub.TextScaled = true
	self.sub.TextColor3 = Color3.fromRGB(190, 195, 225)
	self.sub.Parent = card
end

function TableController:_refreshStatus()
	local text, sub, color
	if self.phase == "Empty" then
		text, sub, color = "AVAILABLE", self.name .. "  •  sit down to play  •  0/2", COLORS.Empty
	elseif self.phase == "Waiting" then
		text, sub, color = "WAITING FOR PLAYERS", self.name .. "  •  1/2 seated", COLORS.Waiting
	else
		local a, b = self.players[1], self.players[2]
		local names = (a and a.DisplayName or "?") .. " vs " .. (b and b.DisplayName or "?")
		text, sub, color = "IN PROGRESS", names .. "  •  Round " .. math.max(1, self.round), COLORS.Busy
	end
	self.title.Text = text
	self.title.TextColor3 = color
	self.sub.Text = sub
	TweenService:Create(self.light, TweenInfo.new(0.5), { Color = color }):Play()
end

function TableController:_pulse()
	TweenService:Create(self.light, TweenInfo.new(0.25, Enum.EasingStyle.Quad, Enum.EasingDirection.Out, 0, true), { Transparency = 0.75 }):Play()
end

-- ===== helpers =====
function TableController:_after(seconds, fn)
	local t = self.token
	task.delay(seconds, function()
		if self.token == t then fn() end
	end)
end

function TableController:_setter() return self.players[self.setterIndex] end
function TableController:_guesser() return self.players[3 - self.setterIndex] end

function TableController:_notice(player, text, kind)
	pcall(function()
		self.deps.remotes.Notice:FireClient(player, text, kind or "info")
	end)
end

function TableController:_entry(p, role)
	return { name = p.DisplayName, userId = p.UserId, role = role, score = self.scores[p.UserId] or 0 }
end

function TableController:_payload(player)
	local p = { table = self.name, phase = self.phase, round = self.round, endsAt = self.endsAt }
	if self.phase == "Empty" or self.phase == "Waiting" or #self.players < 2 then
		p.phase = "Waiting"
		p.players = {}
		for _, pl in ipairs(self.players) do
			table.insert(p.players, self:_entry(pl, nil))
		end
		return p
	end
	local setter, guesser = self:_setter(), self:_guesser()
	p.role = (player == setter) and "Setter" or "Guesser"
	p.players = { self:_entry(setter, "Setter"), self:_entry(guesser, "Guesser") }
	p.history = self.history
	p.hint = self.hint
	-- SECURITY: the secret is only ever sent to the Setter, or to everyone once the round is over.
	if self.secret and (player == setter or self.phase == "RoundOver") then
		p.secret = self.secret
	end
	if self.phase == "Choosing" and player == setter then
		p.suggestions = self.suggestions
	end
	if self.phase == "RoundOver" then
		p.result = self.result
		p.voted = self.votes[player] == true
	end
	return p
end

function TableController:_sync()
	for _, p in ipairs(self.players) do
		pcall(function()
			self.deps.remotes.MatchUpdate:FireClient(p, self:_payload(p))
		end)
	end
end

function TableController:_resetMatch()
	self.secret = nil
	self.hint = nil
	self.result = nil
	self.history = {}
	self.scores = {}
	self.votes = {}
	self.round = 0
	self.setterIndex = 1
	self.endsAt = 0
end

-- ===== seating =====
function TableController:_onOccupant(seat)
	local hum = seat.Occupant
	local newPlayer = hum and Players:GetPlayerFromCharacter(hum.Parent) or nil
	local old = self.occupants[seat]
	if old == newPlayer then return end
	self.occupants[seat] = newPlayer
	if old then self:Leave(old) end
	if newPlayer then self:_join(newPlayer) end
end

function TableController:_join(player)
	if table.find(self.players, player) then return end
	self.deps.registry[player] = self
	table.insert(self.players, player)
	self:_pulse()
	if #self.players >= 2 then
		self:_startMatch()
	else
		self.token += 1
		self.phase = "Waiting"
		self:_refreshStatus()
		self:_sync()
	end
end

function TableController:Leave(player)
	local idx = table.find(self.players, player)
	if not idx then return end
	table.remove(self.players, idx)
	if self.deps.registry[player] == self then self.deps.registry[player] = nil end
	self.token += 1
	local wasActive = self.phase ~= "Waiting" and self.phase ~= "Empty"
	pcall(function()
		self.deps.remotes.MatchUpdate:FireClient(player, { phase = "Left", table = self.name })
	end)
	self:_resetMatch()
	if #self.players == 0 then
		self.phase = "Empty"
	else
		self.phase = "Waiting"
		if wasActive then
			self:_notice(self.players[1], "Your opponent left. Waiting for a new player...", "warn")
		end
	end
	self:_refreshStatus()
	self:_sync()
end

-- Called when a player presses the Leave button.
function TableController:RequestLeave(player)
	local hum = player.Character and player.Character:FindFirstChildOfClass("Humanoid")
	if hum then
		hum.Sit = false
		hum.Jump = true
	end
	self:Leave(player)
end

-- ===== match flow =====
function TableController:_startMatch()
	self:_resetMatch()
	for _, p in ipairs(self.players) do self.scores[p.UserId] = 0 end
	self.token += 1
	self.phase = "Starting"
	self.endsAt = workspace:GetServerTimeNow() + CFG.START_DELAY
	self:_refreshStatus()
	self:_sync()
	self:_after(CFG.START_DELAY, function() self:_startRound() end)
end

function TableController:_suggest()
	local list = self.deps.WordData.list
	local picked, seen = {}, {}
	while #picked < 6 do
		local w = list[math.random(#list)]
		if not seen[w] then
			seen[w] = true
			table.insert(picked, w)
		end
	end
	return picked
end

function TableController:_startRound()
	if #self.players < 2 then return end
	self.round += 1
	self.token += 1
	self.phase = "Choosing"
	self.secret = nil
	self.hint = nil
	self.result = nil
	self.history = {}
	self.votes = {}
	self.suggestions = self:_suggest()
	self.endsAt = workspace:GetServerTimeNow() + CFG.CHOOSE_TIME
	self:_refreshStatus()
	self:_sync()
	self:_after(CFG.CHOOSE_TIME, function()
		local list = self.deps.WordData.list
		self:_beginGuessing(list[math.random(#list)])
	end)
end

function TableController:_beginGuessing(word)
	self.secret = word
	self.token += 1
	self.phase = "Guessing"
	self.endsAt = workspace:GetServerTimeNow() + CFG.GUESS_TIME
	self:_sync()
	self:_after(CFG.GUESS_TIME, function() self:_endRound(false) end)
end

-- Setter submits the secret word.
function TableController:SetWord(player, raw)
	if self.phase ~= "Choosing" or player ~= self:_setter() then return end
	local word = normalize(raw)
	if not word then
		self:_notice(player, "Type a word first", "warn")
		return
	end
	if not self.deps.WordData.info[word] then
		self:_notice(player, "'" .. word .. "' isn't in the word list. Try a suggestion!", "warn")
		return
	end
	self:_beginGuessing(word)
end

-- Guesser submits a guess.
function TableController:Guess(player, raw)
	if self.phase ~= "Guessing" or player ~= self:_guesser() then return end
	local now = os.clock()
	if now - (self.lastGuess[player] or 0) < CFG.GUESS_COOLDOWN then return end
	self.lastGuess[player] = now

	local word = normalize(raw)
	if not word then
		self:_notice(player, "Type a word first", "warn")
		return
	end
	if not self.deps.WordData.info[word] then
		self:_notice(player, "'" .. word .. "' isn't in the word list", "warn")
		return
	end
	for _, h in ipairs(self.history) do
		if h.word == word then
			self:_notice(player, "You already tried that one", "warn")
			return
		end
	end

	local exact = (word == self.secret)
	local score = exact and 1 or self.deps.Similarity.score(word, self.secret)
	local prev = self.history[#self.history]
	local trend = "first"
	if prev then
		local d = score - prev.score
		trend = (d >= 0.03 and "warmer") or (d <= -0.03 and "colder") or "same"
	end
	table.insert(self.history, {
		word = word,
		label = self.deps.Similarity.label(score, exact),
		score = score,
		pct = math.floor(score * 100 + 0.5),
		trend = trend,
	})

	if exact then
		self:_endRound(true)
		return
	end

	local info = self.deps.WordData.info[self.secret]
	if #self.history >= 12 then
		self.hint = ("Category: %s  •  Starts with %s"):format(info.cat, self.secret:sub(1, 1):upper())
	elseif #self.history >= 6 then
		self.hint = "Category: " .. info.cat
	end
	self:_sync()
end

function TableController:_endRound(won)
	if self.phase ~= "Guessing" then return end -- prevents double scoring
	self.token += 1
	local guesses = #self.history
	local remaining = math.max(0, self.endsAt - workspace:GetServerTimeNow())
	local gPts, sPts = 0, 0
	if won then
		gPts = math.min(130, 40 + math.max(0, 60 - (guesses - 1) * 6) + math.floor(remaining / 4))
		sPts = math.min(50, 10 + guesses * 5)
	else
		sPts = 60
	end

	local setter, guesser = self:_setter(), self:_guesser()
	self.scores[setter.UserId] = (self.scores[setter.UserId] or 0) + sPts
	self.scores[guesser.UserId] = (self.scores[guesser.UserId] or 0) + gPts
	addStat(setter, "Points", sPts)
	addStat(guesser, "Points", gPts)
	if won then addStat(guesser, "Wins", 1) end

	self.result = {
		won = won,
		word = self.secret,
		guesses = guesses,
		awards = {
			{ name = setter.DisplayName, role = "Setter", pts = sPts },
			{ name = guesser.DisplayName, role = "Guesser", pts = gPts },
		},
	}
	self.phase = "RoundOver"
	self.votes = {}
	self.endsAt = workspace:GetServerTimeNow() + CFG.RESULT_TIME
	self:_refreshStatus()
	self:_sync()
	self:_after(CFG.RESULT_TIME, function() self:_nextRound() end)
end

function TableController:_nextRound()
	self.setterIndex = 3 - self.setterIndex -- swap roles
	self:_startRound()
end

function TableController:PlayAgain(player)
	if self.phase ~= "RoundOver" or not table.find(self.players, player) then return end
	self.votes[player] = true
	if self.votes[self.players[1]] and self.votes[self.players[2]] then
		self.token += 1
		self:_nextRound()
	else
		self:_sync()
		local other = (player == self.players[1]) and self.players[2] or self.players[1]
		if other then self:_notice(other, player.DisplayName .. " wants to play again!", "good") end
	end
end

return TableController
