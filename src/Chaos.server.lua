local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local Lighting = game:GetService("Lighting")
local Debris = game:GetService("Debris")

Lighting.ClockTime = 0

-- Same IDs as the Admin script
local ADMINS = {
	-- 123456789,
}

local function isAdmin(player)
	if RunService:IsStudio() then return true end
	if game.CreatorType == Enum.CreatorType.User and player.UserId == game.CreatorId then
		return true
	end
	return table.find(ADMINS, player.UserId) ~= nil
end

local function allHumanoids()
	local list = {}
	for _, p in ipairs(Players:GetPlayers()) do
		local h = p.Character and p.Character:FindFirstChildOfClass("Humanoid")
		if h then table.insert(list, h) end
	end
	return list
end

local function banner(text, color)
	for _, p in ipairs(Players:GetPlayers()) do
		local pg = p:FindFirstChild("PlayerGui")
		if pg then
			local gui = Instance.new("ScreenGui")
			gui.Name = "EventBanner"
			gui.ResetOnSpawn = false
			local label = Instance.new("TextLabel")
			label.Size = UDim2.new(1, 0, 0, 60)
			label.Position = UDim2.new(0, 0, 0.12, 0)
			label.BackgroundTransparency = 1
			label.Text = text
			label.TextScaled = true
			label.Font = Enum.Font.FredokaOne
			label.TextColor3 = color
			label.TextStrokeTransparency = 0
			label.Parent = gui
			gui.Parent = pg
			Debris:AddItem(gui, 4)
		end
	end
end

-- Random chaos events every 75 seconds, each lasts 20 seconds
local events = {
	{ name = "LOW GRAVITY!", color = Color3.fromRGB(120, 200, 255),
		on = function() workspace.Gravity = 60 end,
		off = function() workspace.Gravity = 196.2 end },
	{ name = "SPEED RUSH!", color = Color3.fromRGB(255, 220, 0),
		on = function() for _, h in ipairs(allHumanoids()) do h.WalkSpeed = 36 end end,
		off = function() for _, h in ipairs(allHumanoids()) do h.WalkSpeed = 16 end end },
	{ name = "SUPER JUMP!", color = Color3.fromRGB(120, 255, 120),
		on = function() for _, h in ipairs(allHumanoids()) do h.UseJumpPower = true h.JumpPower = 110 end end,
		off = function() for _, h in ipairs(allHumanoids()) do h.JumpPower = 50 end end },
}

local running = false
local function runEvent(ev)
	if running then return end
	running = true
	banner(ev.name, ev.color)
	ev.on()
	task.wait(20)
	ev.off()
	running = false
	banner("Back to normal...", Color3.new(1, 1, 1))
end

task.spawn(function()
	while true do
		task.wait(75)
		runEvent(events[math.random(#events)])
	end
end)

-- Extra admin commands: !rocket !fling !giant !tiny !normal !event
local function findPlayers(speaker, text)
	text = (text or "me"):lower()
	if text == "me" then return { speaker } end
	if text == "all" then return Players:GetPlayers() end
	local found = {}
	for _, p in ipairs(Players:GetPlayers()) do
		if p.Name:lower():sub(1, #text) == text or p.DisplayName:lower():sub(1, #text) == text then
			table.insert(found, p)
		end
	end
	return found
end

local function setScale(char, s)
	local hum = char and char:FindFirstChildOfClass("Humanoid")
	if not hum then return end
	for _, n in ipairs({ "BodyHeightScale", "BodyWidthScale", "BodyDepthScale", "HeadScale" }) do
		local v = hum:FindFirstChild(n)
		if v then v.Value = s end
	end
end

local function hrpOf(p) return p.Character and p.Character:FindFirstChild("HumanoidRootPart") end

local commands = {
	rocket = function(_, targets)
		for _, p in ipairs(targets) do
			local r = hrpOf(p)
			if r then r.AssemblyLinearVelocity = Vector3.new(0, 250, 0) end
		end
	end,
	fling = function(_, targets)
		for _, p in ipairs(targets) do
			local r = hrpOf(p)
			if r then
				local h = p.Character:FindFirstChildOfClass("Humanoid")
				h.PlatformStand = true
				r.AssemblyLinearVelocity = Vector3.new(math.random(-300, 300), 150, math.random(-300, 300))
				task.delay(2.5, function() if h.Parent then h.PlatformStand = false end end)
			end
		end
	end,
	giant = function(_, targets) for _, p in ipairs(targets) do setScale(p.Character, 3) end end,
	tiny = function(_, targets) for _, p in ipairs(targets) do setScale(p.Character, 0.4) end end,
	normal = function(_, targets) for _, p in ipairs(targets) do setScale(p.Character, 1) end end,
	event = function() runEvent(events[math.random(#events)]) end,
}

local function onPlayer(player)
	player.Chatted:Connect(function(msg)
		if not isAdmin(player) then return end
		local parts = msg:split(" ")
		local cmd = parts[1]:lower()
		if cmd:sub(1, 1) ~= "!" then return end
		local fn = commands[cmd:sub(2)]
		if fn then fn(player, findPlayers(player, parts[2])) end
	end)
end

Players.PlayerAdded:Connect(onPlayer)
for _, p in ipairs(Players:GetPlayers()) do onPlayer(p) end
