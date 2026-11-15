local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")

local player = Players.LocalPlayer
local mouse = player:GetMouse()
local gunEvent = RS:WaitForChild("GunFire")

local function hook(tool)
	if tool:IsA("Tool") and tool:GetAttribute("Gun") then
		tool.Activated:Connect(function()
			gunEvent:FireServer(mouse.Hit.Position)
		end)
	end
end

player.CharacterAdded:Connect(function()
	local backpack = player:WaitForChild("Backpack")
	for _, t in ipairs(backpack:GetChildren()) do hook(t) end
	backpack.ChildAdded:Connect(hook)
end)
