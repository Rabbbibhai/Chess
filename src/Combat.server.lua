local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local Debris = game:GetService("Debris")
local RunService = game:GetService("RunService")

Players.RespawnTime = 2

local WEAPONS = {
	Pistol  = { damage = 18, rate = 0.3,  pellets = 1, spread = 0.01, range = 300, auto = false, color = Color3.fromRGB(230, 230, 230), size = Vector3.new(0.4, 0.9, 1.8) },
	SMG     = { damage = 8,  rate = 0.08, pellets = 1, spread = 0.05, range = 250, auto = true,  color = Color3.fromRGB(255, 140, 0),   size = Vector3.new(0.5, 1, 2.2) },
	Shotgun = { damage = 12, rate = 0.9,  pellets = 8, spread = 0.14, range = 90,  auto = false, color = Color3.fromRGB(255, 60, 60),   size = Vector3.new(0.6, 0.8, 3) },
	Sniper  = { damage = 75, rate = 1.5,  pellets = 1, spread = 0,    range = 800, auto = false, color = Color3.fromRGB(0, 240, 255),   size = Vector3.new(0.4, 0.6, 4) },
	Rocket  = { damage = 70, rate = 1.6,  pellets = 1, spread = 0,    range = 500, auto = false, explosive = true, radius = 16, color = Color3.fromRGB(255, 220, 0), size = Vector3.new(0.8, 0.8, 3) },
}

local fireEvent = Instance.new("RemoteEvent")
fireEvent.Name = "WeaponFire"
fireEvent.Parent = RS

local lastHit = setmetatable({}, { __mode = "k" })   -- humanoid -> {player, t}
local lastFire = setmetatable({}, { __mode = "k" })  -- tool -> time

local function makeWeapon(name)
	local cfg = WEAPONS[name]
	local tool = Instance.new("Tool")
	tool.Name = name
	tool.CanBeDropped = false
	tool:SetAttribute("Weapon", name)
	tool:SetAttribute("Auto", cfg.auto)
	tool:SetAttribute("Rate", cfg.rate)
	local handle = Instance.new("Part")
	handle.Name = "Handle"
	handle.Size = cfg.size
	handle.Color = cfg.color
	handle.Material = Enum.Material.Neon
	handle.CanCollide = false
	handle.Parent = tool
	return tool
end

local function hasWeapon(player, name)
	for _, container in ipairs({ player:FindFirstChild("Backpack"), player.Character }) do
		if container and container:FindFirstChild(name) then return true end
	end
	return false
end

local function giveWeapon(player, name)
	local bp = player:FindFirstChild("Backpack")
	if not bp or hasWeapon(player, name) then return false end
	makeWeapon(name).Parent = bp
	return true
end

-- Kill feed
local function feed(text)
	for _, p in ipairs(Players:GetPlayers()) do
		local pg = p:FindFirstChild("PlayerGui")
		if pg then
			local gui = Instance.new("ScreenGui")
			gui.Name = "KillFeed"
			gui.ResetOnSpawn = false
			local label = Instance.new("TextLabel")
			label.Size = UDim2.new(1, 0, 0, 34)
			label.Position = UDim2.new(0, 0, 0.22, 0)
			label.BackgroundTransparency = 1
			label.Text = text
			label.TextScaled = true
			label.Font = Enum.Font.FredokaOne
			label.TextColor3 = Color3.fromRGB(255, 90, 90)
			label.TextStrokeTransparency = 0
			label.Parent = gui
			gui.Parent = pg
			Debris:AddItem(gui, 3)
		end
	end
end

local function tracer(from, to, color)
	local len = (to - from).Magnitude
	if len < 1 then return end
	local p = Instance.new("Part")
	p.Anchored = true
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.Material = Enum.Material.Neon
	p.Color = color
	p.Size = Vector3.new(0.15, 0.15, len)
	p.CFrame = CFrame.lookAt(from, to) * CFrame.new(0, 0, -len / 2)
	p.Parent = workspace
	Debris:AddItem(p, 0.07)
end

local function explode(attacker, pos, cfg)
	local e = Instance.new("Explosion")
	e.Position = pos
	e.BlastRadius = cfg.radius
	e.BlastPressure = 0
	e.DestroyJointRadiusPercent = 0
	e.Parent = workspace
	for _, p in ipairs(Players:GetPlayers()) do
		local c = p.Character
		local hrp = c and c:FindFirstChild("HumanoidRootPart")
		local hum = c and c:FindFirstChildOfClass("Humanoid")
		if hrp and hum and hum.Health > 0 then
			local off = hrp.Position - pos
			local dist = off.Magnitude
			if dist < cfg.radius then
				local f = 1 - dist / cfg.radius
				local dmg = cfg.damage * f
				if p == attacker then dmg = dmg * 0.4 end
				lastHit[hum] = { player = attacker, t = os.clock() }
				hum:TakeDamage(dmg)
				local dir = dist > 0.1 and off.Unit or Vector3.yAxis
				hrp.AssemblyLinearVelocity += dir * 70 * f + Vector3.new(0, 40 * f, 0)
			end
		end
	end
end

local function hitPlayer(attacker, part, dmg)
	local model = part:FindFirstAncestorOfClass("Model")
	local hum = model and model:FindFirstChildOfClass("Humanoid")
	if not hum or hum.Health <= 0 or model == attacker.Character then return end
	if part.Name == "Head" then dmg = dmg * 2 end
	lastHit[hum] = { player = attacker, t = os.clock() }
	hum:TakeDamage(dmg)
end

fireEvent.OnServerEvent:Connect(function(player, targetPos)
	if typeof(targetPos) ~= "Vector3" then return end
	local char = player.Character
	local hum = char and char:FindFirstChildOfClass("Humanoid")
	local head = char and char:FindFirstChild("Head")
	if not hum or hum.Health <= 0 or not head then return end
	local tool = char:FindFirstChildOfClass("Tool")
	local cfg = tool and WEAPONS[tool:GetAttribute("Weapon") or ""]
	if not cfg then return end

	local now = os.clock()
	if now - (lastFire[tool] or 0) < cfg.rate * 0.9 then return end
	lastFire[tool] = now

	local origin = head.Position
	local base = (targetPos - origin)
	if base.Magnitude < 0.1 then return end
	base = base.Unit
	local handle = tool:FindFirstChild("Handle")
	local muzzle = handle and handle.Position or origin

	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = { char }

	for _ = 1, cfg.pellets do
		local jitter = Vector3.new(math.random() - 0.5, math.random() - 0.5, math.random() - 0.5) * 2 * cfg.spread
		local dir = (base + jitter).Unit
		local result = workspace:Raycast(origin, dir * cfg.range, params)
		local hitPos = result and result.Position or origin + dir * cfg.range
		tracer(muzzle, hitPos, cfg.color)
		if result then
			if cfg.explosive then
				explode(player, hitPos, cfg)
			else
				hitPlayer(player, result.Instance, cfg.damage)
			end
		end
	end
end)

-- Players: stats, spawn protection, starter pistol, kill credit
local function onCharacter(player, char)
	local hum = char:WaitForChild("Humanoid")
	local ff = Instance.new("ForceField")
	ff.Parent = char
	Debris:AddItem(ff, 3)

	hum.Died:Connect(function()
		local stats = player:FindFirstChild("leaderstats")
		if stats then stats.Deaths.Value += 1 end
		local info = lastHit[hum]
		if info and info.player ~= player and info.player.Parent and os.clock() - info.t < 15 then
			local ks = info.player:FindFirstChild("leaderstats")
			if ks then ks.Kills.Value += 1 end
			feed(info.player.Name .. " eliminated " .. player.Name)
		else
			feed(player.Name .. " fell")
		end
	end)

	player:WaitForChild("Backpack")
	task.wait(0.2)
	giveWeapon(player, "Pistol")
end

local function onPlayer(player)
	local stats = Instance.new("Folder")
	stats.Name = "leaderstats"
	stats.Parent = player
	for _, n in ipairs({ "Kills", "Deaths" }) do
		local v = Instance.new("IntValue")
		v.Name = n
		v.Parent = stats
	end
	player.CharacterAdded:Connect(function(char) onCharacter(player, char) end)
	if player.Character then task.spawn(onCharacter, player, player.Character) end
end

Players.PlayerAdded:Connect(onPlayer)
for _, p in ipairs(Players:GetPlayers()) do onPlayer(p) end

-- Weapon pickups (floors, walls, roofs): "Pickup:WeaponName:id"
local pickups = workspace:WaitForChild("Pickups")
local spinning = {}
for _, p in ipairs(pickups:GetChildren()) do
	local wname = p.Name:split(":")[2]
	if WEAPONS[wname] then
		table.insert(spinning, p)
		local gui = Instance.new("BillboardGui")
		gui.Size = UDim2.new(0, 90, 0, 24)
		gui.StudsOffset = Vector3.new(0, 2.5, 0)
		gui.AlwaysOnTop = true
		gui.Parent = p
		local label = Instance.new("TextLabel")
		label.Size = UDim2.new(1, 0, 1, 0)
		label.BackgroundTransparency = 1
		label.Text = wname
		label.TextScaled = true
		label.Font = Enum.Font.FredokaOne
		label.TextColor3 = p.Color
		label.TextStrokeTransparency = 0
		label.Parent = gui

		local ready = true
		p.Touched:Connect(function(hit)
			if not ready then return end
			local player = Players:GetPlayerFromCharacter(hit.Parent)
			local h = player and hit.Parent:FindFirstChildOfClass("Humanoid")
			if not h or h.Health <= 0 then return end
			if giveWeapon(player, wname) then
				ready = false
				p.Transparency = 1
				gui.Enabled = false
				task.delay(12, function()
					ready = true
					p.Transparency = 0
					gui.Enabled = true
				end)
			end
		end)
	end
end
RunService.Heartbeat:Connect(function(dt)
	for _, p in ipairs(spinning) do
		p.CFrame = p.CFrame * CFrame.Angles(0, dt * 2, 0)
	end
end)
