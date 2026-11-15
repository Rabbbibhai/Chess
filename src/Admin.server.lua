local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

-- Add friends' UserIds here. You (the game owner) are admin automatically.
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

local gunEvent = Instance.new("RemoteEvent")
gunEvent.Name = "GunFire"
gunEvent.Parent = RS

-- Make someone fall / ragdoll and fly back
local function fall(char, direction, power, duration)
	local hum = char and char:FindFirstChildOfClass("Humanoid")
	local hrp = char and char:FindFirstChild("HumanoidRootPart")
	if not hum or not hrp or hum.Health <= 0 then return end
	power = power or 70
	duration = duration or 2.5
	hum.PlatformStand = true
	hum:ChangeState(Enum.HumanoidStateType.Ragdoll)
	hrp.AssemblyLinearVelocity = direction.Unit * power + Vector3.new(0, power * 0.6, 0)
	task.delay(duration, function()
		if hum.Parent and hum.Health > 0 then
			hum.PlatformStand = false
			hum:ChangeState(Enum.HumanoidStateType.GettingUp)
		end
	end)
end

local function makeTool(name, size, color)
	local tool = Instance.new("Tool")
	tool.Name = name
	tool.CanBeDropped = false
	local handle = Instance.new("Part")
	handle.Name = "Handle"
	handle.Size = size
	handle.Color = color
	handle.Parent = tool
	return tool, handle
end

local function giveSlap(player)
	local tool, handle = makeTool("Slap", Vector3.new(1, 1, 2), Color3.fromRGB(255, 200, 120))
	local cooldown = false
	tool.Activated:Connect(function()
		if cooldown then return end
		cooldown = true
		local char = tool.Parent
		local myHrp = char and char:FindFirstChild("HumanoidRootPart")
		if myHrp then
			for _, other in ipairs(Players:GetPlayers()) do
				local oc = other.Character
				local ohrp = oc and oc:FindFirstChild("HumanoidRootPart")
				if oc ~= char and ohrp then
					local offset = ohrp.Position - myHrp.Position
					if offset.Magnitude < 7 and myHrp.CFrame.LookVector:Dot(offset.Unit) > 0.3 then
						fall(oc, Vector3.new(offset.X, 0.01, offset.Z), 80, 2.5)
					end
				end
			end
		end
		task.wait(0.8)
		cooldown = false
	end)
	tool.Parent = player.Backpack
end

local function giveGun(player)
	local tool = makeTool("Fall Gun", Vector3.new(0.6, 1, 2.5), Color3.fromRGB(60, 60, 70))
	tool:SetAttribute("Gun", true)
	tool.Parent = player.Backpack
end

local function giveAdminTools(player)
	giveSlap(player)
	giveGun(player)
end

-- Gun fire (server checks everything)
local lastShot = {}
gunEvent.OnServerEvent:Connect(function(player, targetPos)
	if not isAdmin(player) or typeof(targetPos) ~= "Vector3" then return end
	local char = player.Character
	local tool = char and char:FindFirstChildOfClass("Tool")
	if not tool or not tool:GetAttribute("Gun") then return end
	if os.clock() - (lastShot[player] or 0) < 0.4 then return end
	lastShot[player] = os.clock()

	local head = char:FindFirstChild("Head")
	if not head then return end
	local dir = (targetPos - head.Position).Unit * 300
	local params = RaycastParams.new()
	params.FilterDescendantsInstances = { char }
	local result = workspace:Raycast(head.Position, dir, params)
	if result then
		local hitChar = result.Instance:FindFirstAncestorOfClass("Model")
		if hitChar and hitChar:FindFirstChildOfClass("Humanoid") then
			fall(hitChar, dir, 90, 3)
		end
	end
end)

-- Chat commands: !slap, !fall, !gun, !kill, !tp, !speed, !stage
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

local commands = {
	fall = function(speaker, targets)
		for _, p in ipairs(targets) do
			fall(p.Character, Vector3.new(math.random(-10, 10), 1, math.random(-10, 10)), 60, 3)
		end
	end,
	kill = function(speaker, targets)
		for _, p in ipairs(targets) do
			local h = p.Character and p.Character:FindFirstChildOfClass("Humanoid")
			if h then h.Health = 0 end
		end
	end,
	gun = function(speaker, targets)
		for _, p in ipairs(targets) do giveGun(p) end
	end,
	slap = function(speaker, targets)
		for _, p in ipairs(targets) do giveSlap(p) end
	end,
	tp = function(speaker, targets)
		local mine = speaker.Character and speaker.Character:FindFirstChild("HumanoidRootPart")
		if not mine then return end
		for _, p in ipairs(targets) do
			if p ~= speaker and p.Character then
				p.Character:PivotTo(mine.CFrame * CFrame.new(0, 0, -4))
			end
		end
	end,
	speed = function(speaker, targets, arg)
		for _, p in ipairs(targets) do
			local h = p.Character and p.Character:FindFirstChildOfClass("Humanoid")
			if h then h.WalkSpeed = tonumber(arg) or 16 end
		end
	end,
	stage = function(speaker, targets, arg)
		for _, p in ipairs(targets) do
			local s = p:FindFirstChild("leaderstats") and p.leaderstats:FindFirstChild("Stage")
			if s then s.Value = tonumber(arg) or 0 end
			local h = p.Character and p.Character:FindFirstChildOfClass("Humanoid")
			if h then h.Health = 0 end -- respawns at that stage
		end
	end,
}

local function onPlayer(player)
	player.CharacterAdded:Connect(function()
		if isAdmin(player) then
			task.wait(0.5)
			giveAdminTools(player)
		end
	end)

	player.Chatted:Connect(function(msg)
		if not isAdmin(player) then return end
		local parts = msg:split(" ")
		local cmd = parts[1]:lower()
		if cmd:sub(1, 1) ~= "!" then return end
		local fn = commands[cmd:sub(2)]
		if fn then
			-- !stage <name> <number>, !speed <name> <number>
			fn(player, findPlayers(player, parts[2]), parts[3])
		end
	end)
end

Players.PlayerAdded:Connect(onPlayer)
for _, p in ipairs(Players:GetPlayers()) do onPlayer(p) end
