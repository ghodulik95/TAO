import pandas

def get_prefix(s):
  last_underscore_index = s.rfind('_')
  prefix = s[0 : last_underscore_index]
  return prefix

def get_ratios(values, included_cutoff = float('inf')):
  counts_of_prefixes = {}
  counts = {}

  for value in values:
    if value[0] > included_cutoff:
      continue
    if value[1] not in counts:
      counts[value[1]] = 0
    counts[value[1]] += 1
    prefix = get_prefix(value[1])
    if prefix not in counts_of_prefixes:
      counts_of_prefixes[prefix] = 0
    counts_of_prefixes[prefix] += 1
  
  ratios = {}
  for value, count in counts.items():
    ratio = float(count) / counts_of_prefixes[get_prefix(value)]
    ratios[value] = ratio
    
  return ratios
  
def get_scores(values, included_cutoff, comparison_ratios):
  ratios = get_ratios(values, included_cutoff)
  scores = {}
  for value, ratio in ratios.items():
    score = (ratio - comparison_ratios[value]) / comparison_ratios[value]
    scores[value] = score
  return scores

class TAODataSelection:
  
  def __init__(self, conditions, data_file):
    file_name = "allVaccineRuns.csv"
    if data_file == "noVaccines" :
      file_name = "allNoVaccine.csv"
    data = pandas.read_csv(file_name)
    for condition in conditions.items():
      data = data[data[condition[0]] == condition[1]]
    self.data = data
    
    column_names = data.columns
    self.values = []
    for index, row in data.iterrows():
      conditions_met = True
      for condition in conditions.items():
        if str(row[condition[0]]) != str(condition[1]):
          conditions_met = False
        break
      if not conditions_met:
        continue
      percInfected = row['cumulativeInfections'] / 3000
      for column_name in column_names:
        if column_name == "cumulativeInfections":
          break
        value = column_name + "_" + str(row[column_name])
        self.values.append([percInfected, value])
    self.original_ratios = get_ratios(self.values)
    
  def get_scores(self, perc_cutoff):
    scores = get_scores(self.values, perc_cutoff, self.original_ratios)

    positives = {k : v for k, v in scores.items() if v > 0}
    positives = {k : v for k, v in sorted(positives.items(), key= lambda item: -item[1])}
    negatives = {k : v for k, v in scores.items() if v <= 0}
    negatives = {k : v for k, v in sorted(negatives.items(), key= lambda item: item[1])}

    positives_df = pandas.DataFrame(positives.items(), columns = ["Intervention", "Score"])
    negatives_df = pandas.DataFrame(negatives.items(), columns = ["Intervention", "Score"])
    return positives_df, negatives_df
    
global_cache = {}
global_return = None
positives_df = None
negatives_df = None
df = None
def get_tao_scores(cutoff, conditions, data_file):
  key = tuple(data_file) + tuple(sorted(conditions.items()))
  if key not in global_cache:
    global_cache[key] = TAODataSelection(conditions, data_file)
  global positives_df, negatives_df, df
  positives_df, negatives_df = global_cache[key].get_scores(cutoff)
  df = global_cache[key].data
