local Players = game:GetService("Players")

local checkpoints = workspace:WaitForChild("Checkpoints")
local killBricks = workspace:WaitForChild("KillBricks")

local function setupPlayer(player)
	local stats = Instance.new("Folder")
	stats.Name = "leaderstats"
	stats.Parent = player

	local stage = Instance.new("IntValue")
	stage.Name = "Stage"
	stage.Value = 0
	stage.Parent = stats

	player.CharacterAdded:Connect(function(char)
		local hrp = char:WaitForChild("HumanoidRootPart")
		local cp = checkpoints:FindFirstChild(tostring(stage.Value))
		if cp then
			task.wait()
			char:PivotTo(cp.CFrame + Vector3.new(0, 4, 0))
		end
	end)
end

Players.PlayerAdded:Connect(setupPlayer)
for _, p in ipairs(Players:GetPlayers()) do setupPlayer(p) end

-- Checkpoints
for _, part in ipairs(checkpoints:GetChildren()) do
	local num = tonumber(part.Name)
	if num and part:IsA("BasePart") then
		part.Touched:Connect(function(hit)
			local player = Players:GetPlayerFromCharacter(hit.Parent)
			if player and player:FindFirstChild("leaderstats") then
				local stage = player.leaderstats.Stage
				if num > stage.Value then
					stage.Value = num
				end
			end
		end)
	end
end

-- Kill bricks
for _, part in ipairs(killBricks:GetDescendants()) do
	if part:IsA("BasePart") then
		part.Touched:Connect(function(hit)
			local hum = hit.Parent:FindFirstChildOfClass("Humanoid")
			if hum then hum.Health = 0 end
		end)
	end
end
