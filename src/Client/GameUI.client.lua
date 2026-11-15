local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")

local UIKit = require(script:WaitForChild("UIKit"))
local new, tween, label = UIKit.new, UIKit.tween, UIKit.label
local C, FB = UIKit.Colors, UIKit.Feedback
local LEFT = Enum.TextXAlignment.Left
local RIGHT = Enum.TextXAlignment.Right
local BACK = Enum.EasingStyle.Back

local player = Players.LocalPlayer
local remotes = RS:WaitForChild("WordRemotes")
local gui = new("ScreenGui", { Name = "WordGameUI", ResetOnSpawn = false, IgnoreGuiInset = true, DisplayOrder = 5 }, player:WaitForChild("PlayerGui"))

local current = nil -- latest state from the server

-- ===== toast =====
local toast = new("TextLabel", {
	Size = UDim2.fromOffset(380, 46), AnchorPoint = Vector2.new(0.5, 0), Position = UDim2.new(0.5, 0, 0, -70),
	BackgroundColor3 = C.panel, TextColor3 = C.text, Font = UIKit.Font, TextSize = 17, Text = "", ZIndex = 50, TextWrapped = true,
}, gui)
UIKit.round(toast, 23)
UIKit.stroke(toast, C.accent, 2)
local toastId = 0
local function notify(text, kind)
	toastId += 1
	local id = toastId
	toast.Text = text
	toast.TextColor3 = (kind == "warn" and C.bad) or (kind == "good" and C.good) or C.text
	tween(toast, 0.35, { Position = UDim2.new(0.5, 0, 0, 16) }, BACK)
	task.delay(3.2, function()
		if toastId == id then tween(toast, 0.3, { Position = UDim2.new(0.5, 0, 0, -70) }) end
	end)
end

-- ===== main panel =====
local panel = new("CanvasGroup", {
	Name = "Panel", Size = UDim2.fromOffset(720, 440), AnchorPoint = Vector2.new(0.5, 0.5),
	Position = UDim2.fromScale(0.5, 0.5), BackgroundColor3 = C.bg, GroupTransparency = 1, Visible = false,
}, gui)
UIKit.round(panel, 20)
UIKit.stroke(panel, C.accent, 2)
local pscale = new("UIScale", {}, panel)
local function fit()
	local vp = gui.AbsoluteSize
	pscale.Scale = math.clamp(math.min(vp.X / 760, vp.Y / 500), 0.4, 1.15)
end
gui:GetPropertyChangedSignal("AbsoluteSize"):Connect(fit)
fit()

local function showPanel()
	panel.Visible = true
	panel.Position = UDim2.fromScale(0.5, 0.62)
	panel.GroupTransparency = 1
	tween(panel, 0.45, { Position = UDim2.fromScale(0.5, 0.5), GroupTransparency = 0 }, BACK)
end
local function hidePanel()
	tween(panel, 0.25, { Position = UDim2.fromScale(0.5, 0.62), GroupTransparency = 1 })
	task.delay(0.3, function()
		if not current then panel.Visible = false end
	end)
end

-- header
local header = new("Frame", { Size = UDim2.new(1, 0, 0, 56), BackgroundColor3 = C.panel, BorderSizePixel = 0 }, panel)
local title = label(header, "", 20, C.text, { Position = UDim2.fromOffset(16, 0), Size = UDim2.new(0.5, 0, 1, 0), TextXAlignment = LEFT })
local timerPill = new("TextLabel", {
	Size = UDim2.fromOffset(100, 34), AnchorPoint = Vector2.new(1, 0.5), Position = UDim2.new(1, -130, 0.5, 0),
	BackgroundColor3 = C.card, Font = UIKit.Font, TextSize = 20, TextColor3 = C.text, Text = "0:00",
}, header)
UIKit.round(timerPill, 17)
local leaveBtn = UIKit.button(header, "Leave", C.bad, { Size = UDim2.fromOffset(100, 34), AnchorPoint = Vector2.new(1, 0.5), Position = UDim2.new(1, -14, 0.5, 0) })
leaveBtn.Activated:Connect(function() remotes.LeaveTable:FireServer() end)

-- player cards
local cards = {}
for i = 1, 2 do
	local f = new("Frame", { Size = UDim2.fromOffset(344, 52), Position = UDim2.fromOffset(i == 1 and 10 or 366, 64), BackgroundColor3 = C.card }, panel)
	UIKit.round(f, 12)
	local st = UIKit.stroke(f, C.card, 2)
	local nameL = label(f, "", 18, C.text, { Position = UDim2.fromOffset(14, 0), Size = UDim2.new(0.5, 0, 1, 0), TextXAlignment = LEFT })
	local chip = new("TextLabel", {
		Size = UDim2.fromOffset(86, 26), AnchorPoint = Vector2.new(1, 0.5), Position = UDim2.new(1, -104, 0.5, 0),
		BackgroundColor3 = C.accent, Font = UIKit.Font, TextSize = 13, TextColor3 = Color3.new(1, 1, 1), Text = "",
	}, f)
	UIKit.round(chip, 13)
	local scoreL = label(f, "0", 20, C.good, { AnchorPoint = Vector2.new(1, 0.5), Position = UDim2.new(1, -12, 0.5, 0), Size = UDim2.fromOffset(84, 30), TextXAlignment = RIGHT })
	cards[i] = { frame = f, stroke = st, name = nameL, chip = chip, score = scoreL }
end

local body = new("Frame", { Position = UDim2.fromOffset(10, 126), Size = UDim2.fromOffset(700, 304), BackgroundTransparency = 1 }, panel)
local function bodyFrame()
	return new("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Visible = false }, body)
end

-- --- waiting / starting ---
local bWait = bodyFrame()
local waitBig = label(bWait, "", 30, C.text, { Size = UDim2.new(1, 0, 0, 60), Position = UDim2.fromOffset(0, 90) })
local waitSmall = label(bWait, "", 18, C.muted, { Font = UIKit.FontMed, Size = UDim2.new(1, 0, 0, 50), Position = UDim2.fromOffset(0, 152) })

-- --- setter chooses word ---
local bChoose = bodyFrame()
label(bChoose, "Pick your secret word", 26, C.text, { Size = UDim2.new(1, 0, 0, 40), Position = UDim2.fromOffset(0, 8) })
local wordBox = new("TextBox", {
	Size = UDim2.fromOffset(420, 52), Position = UDim2.fromOffset(140, 60), PlaceholderText = "Type a word...", Text = "",
	ClearTextOnFocus = false, Font = UIKit.Font, TextSize = 22, TextColor3 = C.text, PlaceholderColor3 = C.muted, BackgroundColor3 = C.card,
}, bChoose)
UIKit.round(wordBox, 12)
local confirmBtn = UIKit.button(bChoose, "Lock it in", C.accent, { Size = UDim2.fromOffset(204, 46), Position = UDim2.fromOffset(140, 122) })
local surpriseBtn = UIKit.button(bChoose, "Surprise me", C.card, { Size = UDim2.fromOffset(204, 46), Position = UDim2.fromOffset(356, 122) })
local chips = new("Frame", { Position = UDim2.fromOffset(0, 190), Size = UDim2.fromOffset(700, 100), BackgroundTransparency = 1 }, bChoose)
new("UIGridLayout", { CellSize = UDim2.fromOffset(212, 40), CellPadding = UDim2.fromOffset(8, 8), HorizontalAlignment = Enum.HorizontalAlignment.Center, SortOrder = Enum.SortOrder.LayoutOrder }, chips)
local chipKey = ""
local suggestions = {}

local function lockIn()
	local t = (wordBox.Text:gsub("%s", ""))
	if #t == 0 then
		notify("Type a word first", "warn")
		return
	end
	remotes.SetWord:FireServer(wordBox.Text)
end
confirmBtn.Activated:Connect(lockIn)
wordBox.FocusLost:Connect(function(enter) if enter then lockIn() end end)
surpriseBtn.Activated:Connect(function()
	if #suggestions > 0 then remotes.SetWord:FireServer(suggestions[math.random(#suggestions)]) end
end)

-- --- guessing ---
local bPlay = bodyFrame()
local secretCard = new("Frame", { Size = UDim2.fromOffset(300, 78), BackgroundColor3 = C.card }, bPlay)
UIKit.round(secretCard, 12)
label(secretCard, "SECRET WORD", 12, C.muted, { Position = UDim2.fromOffset(12, 6), Size = UDim2.fromOffset(276, 16), TextXAlignment = LEFT })
local secretL = label(secretCard, "", 28, C.text, { Position = UDim2.fromOffset(12, 22), Size = UDim2.fromOffset(276, 34), TextXAlignment = LEFT })
local hintL = label(secretCard, "", 12, C.orange, { Position = UDim2.fromOffset(12, 56), Size = UDim2.fromOffset(276, 16), TextXAlignment = LEFT })

local inputCard = new("Frame", { Position = UDim2.fromOffset(0, 88), Size = UDim2.fromOffset(300, 104), BackgroundColor3 = C.card }, bPlay)
UIKit.round(inputCard, 12)
local guessBox = new("TextBox", {
	Size = UDim2.fromOffset(276, 44), Position = UDim2.fromOffset(12, 10), PlaceholderText = "Type a guess...", Text = "",
	ClearTextOnFocus = false, Font = UIKit.Font, TextSize = 20, TextColor3 = C.text, PlaceholderColor3 = C.muted, BackgroundColor3 = C.bg,
}, inputCard)
UIKit.round(guessBox, 10)
local submitBtn = UIKit.button(inputCard, "Submit guess", C.accent, { Size = UDim2.fromOffset(276, 34), Position = UDim2.fromOffset(12, 62) })

local meterCard = new("Frame", { Position = UDim2.fromOffset(0, 202), Size = UDim2.fromOffset(300, 102), BackgroundColor3 = C.card }, bPlay)
UIKit.round(meterCard, 12)
label(meterCard, "CLOSENESS", 12, C.muted, { Position = UDim2.fromOffset(12, 8), Size = UDim2.fromOffset(276, 16), TextXAlignment = LEFT })
local track = new("Frame", { Position = UDim2.fromOffset(12, 30), Size = UDim2.fromOffset(276, 18), BackgroundColor3 = C.bg }, meterCard)
UIKit.round(track, 9)
local fill = new("Frame", { Size = UDim2.fromScale(0, 1), BackgroundColor3 = FB.FREEZING }, track)
UIKit.round(fill, 9)
local fbLabel = label(meterCard, "Make your first guess", 22, C.text, { Position = UDim2.fromOffset(12, 52), Size = UDim2.fromOffset(276, 28), TextXAlignment = LEFT })
local trendLabel = label(meterCard, "", 14, C.muted, { Position = UDim2.fromOffset(12, 80), Size = UDim2.fromOffset(276, 18), TextXAlignment = LEFT })

local histCard = new("Frame", { Position = UDim2.fromOffset(316, 0), Size = UDim2.fromOffset(384, 304), BackgroundColor3 = C.card }, bPlay)
UIKit.round(histCard, 12)
local histTitle = label(histCard, "Guesses", 16, C.text, { Position = UDim2.fromOffset(14, 8), Size = UDim2.fromOffset(356, 24), TextXAlignment = LEFT })
local histList = new("ScrollingFrame", {
	Position = UDim2.fromOffset(8, 38), Size = UDim2.fromOffset(368, 258), BackgroundTransparency = 1, BorderSizePixel = 0,
	ScrollBarThickness = 4, CanvasSize = UDim2.new(), AutomaticCanvasSize = Enum.AutomaticSize.Y,
}, histCard)
new("UIListLayout", { Padding = UDim.new(0, 6), SortOrder = Enum.SortOrder.LayoutOrder }, histList)
local emptyL = label(histCard, "No guesses yet", 16, C.muted, { Font = UIKit.FontMed, Position = UDim2.fromOffset(0, 120), Size = UDim2.new(1, 0, 0, 30) })

local rendered = 0
local lastRound = -1

local function clearRows()
	for _, c in ipairs(histList:GetChildren()) do
		if c:IsA("Frame") then c:Destroy() end
	end
	rendered = 0
end

local TREND = {
	warmer = { "▲ warmer", C.good }, colder = { "▼ colder", FB.COLD },
	same = { "= same", C.muted }, first = { "• first", C.muted },
}

local function addRow(h, index)
	local row = new("Frame", { Size = UDim2.new(1, -8, 0, 46), BackgroundTransparency = 1, LayoutOrder = -index }, histList)
	local inner = new("Frame", { Size = UDim2.fromScale(1, 1), Position = UDim2.fromOffset(40, 0), BackgroundColor3 = C.bg, BackgroundTransparency = 1 }, row)
	UIKit.round(inner, 10)
	local color = FB[h.label] or C.text
	new("Frame", { Size = UDim2.fromOffset(5, 46), BackgroundColor3 = color, BorderSizePixel = 0 }, inner)
	label(inner, h.word:upper(), 17, C.text, { Position = UDim2.fromOffset(16, 0), Size = UDim2.fromOffset(120, 46), TextXAlignment = LEFT })
	local chip = new("TextLabel", { Size = UDim2.fromOffset(92, 24), Position = UDim2.fromOffset(136, 11), BackgroundColor3 = color, Font = UIKit.Font, TextSize = 13, TextColor3 = Color3.fromRGB(20, 20, 30), Text = h.label }, inner)
	UIKit.round(chip, 12)
	local tr = TREND[h.trend] or TREND.first
	label(inner, tr[1], 13, tr[2], { Position = UDim2.fromOffset(236, 0), Size = UDim2.fromOffset(80, 46), TextXAlignment = LEFT })
	label(inner, h.pct .. "%", 15, C.muted, { Position = UDim2.fromOffset(310, 0), Size = UDim2.fromOffset(48, 46), TextXAlignment = RIGHT })
	tween(inner, 0.35, { Position = UDim2.fromOffset(0, 0), BackgroundTransparency = 0 }, BACK)
end

local function updateMeter(last)
	if not last then
		tween(fill, 0.3, { Size = UDim2.fromScale(0, 1) })
		fbLabel.Text = "Make your first guess"
		fbLabel.TextColor3 = C.text
		trendLabel.Text = ""
		return
	end
	local color = FB[last.label] or C.text
	tween(fill, 0.45, { Size = UDim2.fromScale(math.max(0.03, last.score), 1), BackgroundColor3 = color }, BACK)
	fbLabel.Text = last.label
	fbLabel.TextColor3 = color
	local tr = TREND[last.trend] or TREND.first
	trendLabel.Text = tr[1]
	trendLabel.TextColor3 = tr[2]
end

local lastSubmit = 0
local function submit()
	local t = (guessBox.Text:gsub("%s", ""))
	if #t == 0 then
		notify("Type a word first", "warn")
		return
	end
	if os.clock() - lastSubmit < 0.5 then return end
	lastSubmit = os.clock()
	remotes.SubmitGuess:FireServer(guessBox.Text)
	guessBox.Text = ""
	if not UserInputService.TouchEnabled then guessBox:CaptureFocus() end
end
submitBtn.Activated:Connect(submit)
guessBox.FocusLost:Connect(function(enter) if enter then submit() end end)

-- --- round over ---
local bOver = bodyFrame()
local overCard = new("Frame", { Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1 }, bOver)
local overTitle = label(overCard, "", 36, C.good, { Size = UDim2.new(1, 0, 0, 50), Position = UDim2.fromOffset(0, 16) })
local overWord = label(overCard, "", 44, C.accent, { Size = UDim2.new(1, 0, 0, 60), Position = UDim2.fromOffset(0, 72) })
local overInfo = label(overCard, "", 18, C.muted, { Font = UIKit.FontMed, Size = UDim2.new(1, 0, 0, 26), Position = UDim2.fromOffset(0, 136) })
local overAwards = label(overCard, "", 20, C.text, { Size = UDim2.new(1, 0, 0, 62), Position = UDim2.fromOffset(0, 166) })
local playBtn = UIKit.button(bOver, "Play Again (swap roles)", C.accent, { Size = UDim2.fromOffset(260, 46), Position = UDim2.fromOffset(120, 244) })
local leave2 = UIKit.button(bOver, "Leave table", C.card, { Size = UDim2.fromOffset(160, 46), Position = UDim2.fromOffset(396, 244) })
playBtn.Activated:Connect(function() remotes.PlayAgain:FireServer() end)
leave2.Activated:Connect(function() remotes.LeaveTable:FireServer() end)

local function confetti()
	for _ = 1, 50 do
		local startX = math.random()
		local p = new("Frame", {
			Size = UDim2.fromOffset(math.random(6, 12), math.random(10, 18)), Position = UDim2.new(startX, 0, 0, -20),
			BackgroundColor3 = Color3.fromHSV(math.random(), 0.8, 1), Rotation = math.random(0, 360), BorderSizePixel = 0, ZIndex = 20,
		}, panel)
		tween(p, 1.6 + math.random(), { Position = UDim2.new(startX, 0, 1, 20), Rotation = math.random(-360, 360), BackgroundTransparency = 0.4 }, Enum.EasingStyle.Quad, Enum.EasingDirection.In)
		task.delay(3, function() p:Destroy() end)
	end
end

-- ===== render =====
local function render(state)
	if not state or state.phase == "Left" then
		if current then
			current = nil
			hidePanel()
		end
		return
	end
	local first = (current == nil)
	local prevPhase = current and current.phase
	current = state
	if first then showPanel() end

	local phase = state.phase
	title.Text = state.table .. ((state.round and state.round > 0) and ("   •   Round " .. state.round) or "")

	-- player cards
	for i = 1, 2 do
		local c, e = cards[i], state.players and state.players[i]
		c.frame.Visible = e ~= nil
		if e then
			local me = e.userId == player.UserId
			c.name.Text = e.name .. (me and " (you)" or "")
			c.stroke.Color = me and C.accent or C.card
			c.chip.Visible = e.role ~= nil
			c.chip.Text = e.role and e.role:upper() or ""
			c.chip.BackgroundColor3 = e.role == "Setter" and C.accent or C.orange
			c.score.Text = tostring(e.score or 0)
		end
	end

	-- which body to show
	local showWait = phase == "Waiting" or phase == "Starting" or (phase == "Choosing" and state.role == "Guesser")
	bWait.Visible = showWait
	bChoose.Visible = phase == "Choosing" and state.role == "Setter"
	bPlay.Visible = phase == "Guessing"
	bOver.Visible = phase == "RoundOver"

	if phase == "Waiting" then
		waitBig.Text = "Waiting for an opponent..."
		waitSmall.Text = "Players seated: " .. #state.players .. "/2. Invite a friend to sit at this table!"
	elseif phase == "Starting" then
		waitBig.Text = "Match starting!"
		waitSmall.Text = "Get ready..."
	elseif phase == "Choosing" and state.role == "Guesser" then
		waitBig.Text = "Opponent is picking a word..."
		waitSmall.Text = "You are the GUESSER. Warmer or colder will guide you."
	end

	-- setter suggestions
	if phase == "Choosing" and state.role == "Setter" and state.suggestions then
		local key = table.concat(state.suggestions, ",")
		if key ~= chipKey then
			chipKey = key
			suggestions = state.suggestions
			wordBox.Text = ""
			for _, c in ipairs(chips:GetChildren()) do
				if c:IsA("TextButton") then c:Destroy() end
			end
			for i, w in ipairs(state.suggestions) do
				local b = UIKit.button(chips, w:upper(), C.card, { LayoutOrder = i, TextSize = 16 })
				b.Activated:Connect(function() wordBox.Text = w end)
			end
		end
	end

	-- guessing view
	if phase == "Guessing" then
		local isSetter = state.role == "Setter"
		secretL.Text = isSetter and (state.secret or ""):upper() or "? ? ? ? ?"
		hintL.Text = state.hint or (isSetter and "Your opponent is guessing..." or "")
		inputCard.Visible = not isSetter
		meterCard.Position = isSetter and UDim2.fromOffset(0, 88) or UDim2.fromOffset(0, 202)

		local hist = state.history or {}
		if #hist < rendered or state.round ~= lastRound then clearRows() end
		lastRound = state.round
		for i = rendered + 1, #hist do addRow(hist[i], i) end
		rendered = #hist
		emptyL.Visible = #hist == 0
		histTitle.Text = ("Guesses (%d)"):format(#hist)
		updateMeter(hist[#hist])
	end

	-- round over view
	if phase == "RoundOver" and state.result then
		local r = state.result
		overTitle.Text = r.won and "WORD FOUND!" or "TIME'S UP!"
		overTitle.TextColor3 = r.won and C.good or C.bad
		overWord.Text = r.word:upper()
		overInfo.Text = ("%d guess%s"):format(r.guesses, r.guesses == 1 and "" or "es")
		local lines = {}
		for _, a in ipairs(r.awards) do
			table.insert(lines, ("%s (%s)   +%d"):format(a.name, a.role, a.pts))
		end
		overAwards.Text = table.concat(lines, "\n")
		playBtn.Text = state.voted and "Waiting for opponent..." or "Play Again (swap roles)"
		playBtn.BackgroundColor3 = state.voted and C.card or C.accent
		playBtn.Active = not state.voted
		if prevPhase ~= "RoundOver" then
			overCard.Position = UDim2.fromOffset(0, 40)
			tween(overCard, 0.5, { Position = UDim2.fromOffset(0, 0) }, BACK)
			if r.won then confetti() end
		end
	end
end

RunService.Heartbeat:Connect(function()
	if not current or not current.endsAt or current.phase == "Waiting" then
		timerPill.Visible = false
		return
	end
	timerPill.Visible = true
	local left = math.max(0, current.endsAt - workspace:GetServerTimeNow())
	timerPill.Text = string.format("%d:%02d", math.floor(left / 60), math.floor(left % 60))
	timerPill.TextColor3 = left < 10 and C.bad or C.text
end)

remotes.MatchUpdate.OnClientEvent:Connect(render)
remotes.Notice.OnClientEvent:Connect(notify)
