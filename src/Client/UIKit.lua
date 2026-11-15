local TweenService = game:GetService("TweenService")

local UIKit = {}

UIKit.Colors = {
	bg = Color3.fromRGB(22, 24, 38),
	panel = Color3.fromRGB(32, 35, 56),
	card = Color3.fromRGB(46, 50, 80),
	accent = Color3.fromRGB(122, 102, 255),
	orange = Color3.fromRGB(255, 160, 60),
	text = Color3.fromRGB(240, 242, 255),
	muted = Color3.fromRGB(160, 166, 200),
	good = Color3.fromRGB(80, 230, 120),
	bad = Color3.fromRGB(255, 90, 90),
}

UIKit.Feedback = {
	FREEZING = Color3.fromRGB(130, 205, 255),
	COLD = Color3.fromRGB(80, 140, 255),
	COOL = Color3.fromRGB(70, 210, 200),
	WARM = Color3.fromRGB(255, 210, 70),
	HOT = Color3.fromRGB(255, 140, 50),
	BURNING = Color3.fromRGB(255, 60, 60),
	CORRECT = Color3.fromRGB(80, 230, 120),
}

UIKit.Font = Enum.Font.GothamBold
UIKit.FontMed = Enum.Font.Gotham

function UIKit.new(class, props, parent)
	local inst = Instance.new(class)
	for k, v in pairs(props or {}) do inst[k] = v end
	if parent then inst.Parent = parent end
	return inst
end

function UIKit.round(inst, r)
	return UIKit.new("UICorner", { CornerRadius = UDim.new(0, r or 12) }, inst)
end

function UIKit.stroke(inst, color, thickness)
	return UIKit.new("UIStroke", { Color = color, Thickness = thickness or 2, ApplyStrokeMode = Enum.ApplyStrokeMode.Border }, inst)
end

function UIKit.tween(inst, time, props, style, dir)
	local t = TweenService:Create(inst, TweenInfo.new(time, style or Enum.EasingStyle.Quad, dir or Enum.EasingDirection.Out), props)
	t:Play()
	return t
end

function UIKit.label(parent, text, size, color, props)
	local l = Instance.new("TextLabel")
	l.BackgroundTransparency = 1
	l.Font = UIKit.Font
	l.Text = text
	l.TextSize = size
	l.TextColor3 = color
	l.TextWrapped = true
	for k, v in pairs(props or {}) do l[k] = v end
	l.Parent = parent
	return l
end

function UIKit.button(parent, text, color, props)
	local b = Instance.new("TextButton")
	b.Text = text
	b.Font = UIKit.Font
	b.TextSize = 18
	b.TextColor3 = Color3.new(1, 1, 1)
	b.BackgroundColor3 = color
	b.AutoButtonColor = false
	b.BorderSizePixel = 0
	for k, v in pairs(props or {}) do b[k] = v end
	b.Parent = parent
	UIKit.round(b, 10)
	local s = Instance.new("UIScale")
	s.Parent = b
	b.MouseEnter:Connect(function() UIKit.tween(s, 0.12, { Scale = 1.05 }) end)
	b.MouseLeave:Connect(function() UIKit.tween(s, 0.12, { Scale = 1 }) end)
	b.MouseButton1Down:Connect(function() UIKit.tween(s, 0.08, { Scale = 0.94 }) end)
	b.MouseButton1Up:Connect(function() UIKit.tween(s, 0.12, { Scale = 1.05 }) end)
	return b
end

return UIKit
