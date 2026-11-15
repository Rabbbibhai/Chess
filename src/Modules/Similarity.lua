-- Semantic similarity between two vocabulary words (0..1).
-- Combines category/subcategory closeness with shared feature tags.
local WordData = require(script.Parent.WordData)

local Similarity = {}

local THRESHOLDS = {
	{ 0.66, "BURNING" },
	{ 0.52, "HOT" },
	{ 0.38, "WARM" },
	{ 0.24, "COOL" },
	{ 0.10, "COLD" },
}

function Similarity.score(a, b)
	if a == b then return 1 end
	local A, B = WordData.info[a], WordData.info[b]
	if not A or not B then return 0 end

	local base = 0
	if A.sub == B.sub then
		base = 0.45
	elseif A.cat == B.cat then
		base = 0.22
	end

	local shared = 0
	for f in pairs(A.feats) do
		if B.feats[f] then shared += 1 end
	end
	local union = A.n + B.n - shared
	local jaccard = union > 0 and shared / union or 0

	return math.min(0.99, base + 0.6 * jaccard)
end

function Similarity.label(score, exact)
	if exact then return "CORRECT" end
	for _, t in ipairs(THRESHOLDS) do
		if score >= t[1] then return t[2] end
	end
	return "FREEZING"
end

return Similarity
