local Players = game:GetService("Players")
local RS = game:GetService("ReplicatedStorage")
local UIS = game:GetService("UserInputService")

local player = Players.LocalPlayer
local mouse = player:GetMouse()
local fire = RS:WaitForChild("WeaponFire")
local camera = workspace.CurrentCamera

-- Crosshair (also the aim point on phones)
local gui = Instance.new("ScreenGui")
gui.Name = "Crosshair"
gui.ResetOnSpawn = false
gui.IgnoreGuiInset = true
gui.Parent = player:WaitForChild("PlayerGui")
local dot = Instance.new("Frame")
dot.Size = UDim2.new(0, 6, 0, 6)
dot.AnchorPoint = Vector2.new(0.5, 0.5)
dot.Position = UDim2.new(0.5, 0, 0.5, 0)
dot.BackgroundColor3 = Color3.new(1, 1, 1)
dot.BorderSizePixel = 0
dot.Visible = UIS.TouchEnabled and not UIS.MouseEnabled
dot.Parent = gui

local function getAim()
	if UIS.TouchEnabled and not UIS.MouseEnabled then
		local vp = camera.ViewportSize
		local ray = camera:ViewportPointToRay(vp.X / 2, vp.Y / 2)
		local params = RaycastParams.new()
		params.FilterType = Enum.RaycastFilterType.Exclude
		params.FilterDescendantsInstances = { player.Character }
		local r = workspace:Raycast(ray.Origin, ray.Direction * 1000, params)
		return r and r.Position or ray.Origin + ray.Direction * 1000
	end
	return mouse.Hit.Position
end

local hooked = {}
local function hook(tool)
	if hooked[tool] or not tool:IsA("Tool") or not tool:GetAttribute("Weapon") then return end
	hooked[tool] = true
	local holding = false
	tool.Activated:Connect(function()
		holding = true
		repeat
			fire:FireServer(getAim())
			if not tool:GetAttribute("Auto") then break end
			task.wait(tool:GetAttribute("Rate") or 0.2)
		until not holding or tool.Parent ~= player.Character
	end)
	tool.Deactivated:Connect(function() holding = false end)
	tool.Unequipped:Connect(function() holding = false end)
end

local function setup()
	local backpack = player:WaitForChild("Backpack")
	for _, t in ipairs(backpack:GetChildren()) do hook(t) end
	backpack.ChildAdded:Connect(hook)
end

player.CharacterAdded:Connect(setup)
if player.Character then task.spawn(setup) end
