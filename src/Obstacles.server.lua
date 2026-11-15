local TweenService = game:GetService("TweenService")
local RunService = game:GetService("RunService")
local Players = game:GetService("Players")

local function kill(hit)
	local hum = hit.Parent and hit.Parent:FindFirstChildOfClass("Humanoid")
	if hum then hum.Health = 0 end
end

local function humanoidOf(hit)
	return hit.Parent and hit.Parent:FindFirstChildOfClass("Humanoid")
end

-- Moving platforms: "Mover:axis:distance:seconds" (KMover = kills on touch)
for _, p in ipairs(workspace:WaitForChild("Movers"):GetChildren()) do
	local t = p.Name:split(":")
	local axis, dist, secs = t[2], tonumber(t[3]) or 10, tonumber(t[4]) or 3
	local dir = Vector3.new(axis == "x" and 1 or 0, axis == "y" and 1 or 0, axis == "z" and 1 or 0)
	local info = TweenInfo.new(secs, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut, -1, true)
	TweenService:Create(p, info, { CFrame = p.CFrame + dir * dist }):Play()
	if t[1] == "KMover" then p.Touched:Connect(kill) end
end

-- Spinning bars: "Spinner:degreesPerSecond"
local spinners = {}
for _, p in ipairs(workspace:WaitForChild("Spinners"):GetChildren()) do
	local speed = tonumber(p.Name:split(":")[2]) or 90
	table.insert(spinners, { part = p, speed = math.rad(speed) })
	p.Touched:Connect(kill)
end
RunService.Heartbeat:Connect(function(dt)
	for _, s in ipairs(spinners) do
		s.part.CFrame = s.part.CFrame * CFrame.Angles(0, s.speed * dt, 0)
	end
end)

-- Vanishing tiles
for _, p in ipairs(workspace:WaitForChild("Vanish"):GetChildren()) do
	local busy = false
	p.Touched:Connect(function(hit)
		if busy or not humanoidOf(hit) then return end
		busy = true
		task.wait(0.4)
		TweenService:Create(p, TweenInfo.new(0.3), { Transparency = 0.85 }):Play()
		task.wait(0.3)
		p.CanCollide = false
		task.wait(2.5)
		p.CanCollide = true
		p.Transparency = 0
		busy = false
	end)
end

-- Bounce pads: "Bounce:power"
for _, p in ipairs(workspace:WaitForChild("Bouncers"):GetChildren()) do
	local power = tonumber(p.Name:split(":")[2]) or 100
	local recent = {}
	p.Touched:Connect(function(hit)
		local char = hit.Parent
		local hrp = char and char:FindFirstChild("HumanoidRootPart")
		if not hrp or recent[char] then return end
		recent[char] = true
		local v = hrp.AssemblyLinearVelocity
		hrp.AssemblyLinearVelocity = Vector3.new(v.X, power, v.Z)
		task.delay(0.4, function() recent[char] = nil end)
	end)
end

-- Conveyor belts: "Conveyor:axis:speed"
for _, p in ipairs(workspace:WaitForChild("Conveyors"):GetChildren()) do
	local t = p.Name:split(":")
	local speed = tonumber(t[3]) or 20
	p.AssemblyLinearVelocity = (t[2] == "x") and Vector3.new(speed, 0, 0) or Vector3.new(0, 0, speed)
end

-- Finish pad: +1 Win, back to the start
local finish = workspace:WaitForChild("Finish")
local cooling = {}
finish.Touched:Connect(function(hit)
	local player = Players:GetPlayerFromCharacter(hit.Parent)
	if not player or cooling[player] then return end
	cooling[player] = true
	local stats = player:WaitForChild("leaderstats")
	local wins = stats:FindFirstChild("Wins")
	if not wins then
		wins = Instance.new("IntValue")
		wins.Name = "Wins"
		wins.Parent = stats
	end
	wins.Value += 1
	stats.Stage.Value = 0
	task.wait(1.5)
	player:LoadCharacter()
	cooling[player] = nil
end)
