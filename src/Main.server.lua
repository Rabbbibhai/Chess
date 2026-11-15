local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")

local Modules = script.Parent:WaitForChild("Modules")
local WordData = require(Modules:WaitForChild("WordData"))
local Similarity = require(Modules:WaitForChild("Similarity"))
local TableController = require(Modules:WaitForChild("TableController"))

-- Remotes (clients wait for the folder, so parent it last)
local remotes = Instance.new("Folder")
remotes.Name = "WordRemotes"
for _, name in ipairs({ "MatchUpdate", "Notice", "SetWord", "SubmitGuess", "LeaveTable", "PlayAgain" }) do
	local r = Instance.new("RemoteEvent")
	r.Name = name
	r.Parent = remotes
end
remotes.Parent = RS

local registry = {} -- player -> TableController they are seated at
local deps = { remotes = remotes, WordData = WordData, Similarity = Similarity, registry = registry }

for _, model in ipairs(workspace:WaitForChild("Tables"):GetChildren()) do
	TableController.new(model, deps)
end

-- Remote routing: the server decides everything, clients only send intent.
remotes.SetWord.OnServerEvent:Connect(function(player, word)
	local tc = registry[player]
	if tc then tc:SetWord(player, word) end
end)
remotes.SubmitGuess.OnServerEvent:Connect(function(player, word)
	local tc = registry[player]
	if tc then tc:Guess(player, word) end
end)
remotes.PlayAgain.OnServerEvent:Connect(function(player)
	local tc = registry[player]
	if tc then tc:PlayAgain(player) end
end)
remotes.LeaveTable.OnServerEvent:Connect(function(player)
	local tc = registry[player]
	if tc then tc:RequestLeave(player) end
end)

-- Players: leaderboard, welcome, disconnect handling
local function onPlayer(player)
	local stats = Instance.new("Folder")
	stats.Name = "leaderstats"
	for _, n in ipairs({ "Points", "Wins" }) do
		local v = Instance.new("IntValue")
		v.Name = n
		v.Parent = stats
	end
	stats.Parent = player

	local welcomed = false
	player.CharacterAdded:Connect(function()
		if welcomed then return end
		welcomed = true
		task.wait(3)
		pcall(function()
			remotes.Notice:FireClient(player, "Welcome to Word Wars! Sit at a free table to play.", "good")
		end)
	end)
end
Players.PlayerAdded:Connect(onPlayer)
for _, p in ipairs(Players:GetPlayers()) do onPlayer(p) end

Players.PlayerRemoving:Connect(function(player)
	local tc = registry[player]
	if tc then tc:Leave(player) end
end)

-- Lobby sign
local sign = workspace:WaitForChild("Lobby"):WaitForChild("TitleSign")
local sg = Instance.new("SurfaceGui")
sg.Face = Enum.NormalId.Back
sg.CanvasSize = Vector2.new(1280, 240)
sg.Adornee = sign
sg.Parent = sign
local t1 = Instance.new("TextLabel")
t1.BackgroundTransparency = 1
t1.Size = UDim2.fromScale(1, 0.65)
t1.Text = "WORD WARS"
t1.Font = Enum.Font.GothamBlack
t1.TextScaled = true
t1.TextColor3 = Color3.fromRGB(255, 255, 255)
t1.Parent = sg
local t2 = Instance.new("TextLabel")
t2.BackgroundTransparency = 1
t2.Position = UDim2.fromScale(0, 0.65)
t2.Size = UDim2.fromScale(1, 0.3)
t2.Text = "Warmer or Colder  •  Pick a table and sit down!"
t2.Font = Enum.Font.Gotham
t2.TextScaled = true
t2.TextColor3 = Color3.fromRGB(180, 170, 255)
t2.Parent = sg
