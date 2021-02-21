

# How to validate and Autotune algorythm

Today we use an input "Pumpprofile" (used by OAPS/AAPS for looping) and real data from patients (TBR, Boluses, Carbs entered...)

- The difficulty is we get a result, but is difficult to be sure that the new profile better than the previous? and how much better? 
- It's impossible to give an mathematical answer to this question...



The proposal is to replace "user dataset" with a "calculated data set" to be able to give a "mathematic" answer and qualify the efficiency of an autotune algorythm 

Note: I proposed this method for Autotune algorythm but i'm quite sure it could be used for a widest range of applications (i.e. quantify the efficiency of a loop algorythm, test different treatments for slow carbs, compare circadian profile with profiles with single ISF/CSF values...)

To illustrate this proposal, I built an excel file (it's just for showing how we can calculate BG values with a Reference Profile and a PumpProfile and calculate the distance between these profiles, but I think we should use OAPS/AAPS algorythm to build realistics datasets)



## 1 first step build a data set for Autotune Algorythm

- The Idea is to add what I call a "Reference Profile" to modelize a person

  - This Reference Profile is of course unknown by OAPS/AAPS for looping, and is only used to calculate BG values:
    - For basals it's easy, for each 5 minutes, we can calculate the difference between PumpProfile (5 min Basal rate) or TBR treatment (if running) with the Reference Basal rate and use this result to calculate the "real" insulin activity and the "Real" BGI (- Insulin Activity * current "Reference ISF")
    - For Bolus / SMB, same calculation
    - For Meals, I propose to use the same algorythm (exponential) but with dedicated CarbPeak and CarbDuration values, to calculate the Meal "Activity" and the equivalent of BGI for meals (+ Meal Activity * current "Reference CSF" value)
      - In my first draft I include "defaults" values for CarbPeak and CarbDuration, but we can of course customized these values to simulate sugar (smaller values) or a "slow carbs meals" (higher values)
    - For sport/activities, we can also simulate them in calculation with "negative carbs" and dedicated Peak/Duration
  - Each 5 minutes we can calculate the new BG value according to treatments and basals/TBR calculated by OAPS/AAPS algorythm (with pumpprofile)
  - For this reference profile, we can use simple profile (with only one CFS/ISF value) or full circadian profile (with several values)

- Then OAPS/AAPS algortyhm can propose (or not) a new correction: TBR, SMB with pumpProfile

  - We can predefine meals (Carb amount, and if necessary dedicated CarbPeak/CarbDuration for BG calculation) , define if Carbs are UAM or not...
  - OAPS/AAPS can use AMA algorythm (with pumpProfil) for bolus calculation
  - Simulate sport

- This calculation should starts one DIA before first BG value, and could be done for one day (I think it's enough for an autotune algorythm) or more

  

## 2nd step Run Autotune algorythm with dataset built step one

- Now we can make a real evaluation of autotune results, because we know what is the optimum target... It's our Reference profile used for building the dataset and BG calculation!
  - Autotune propose only 20% of target profile and 80% of pumpprofile, but for the efficiency of the algorythm, we can compare full target profile calculated by Autotune algorythm with our Reference profile
- I also propose a method for calculate a "distance" between 2 profiles
  - First calculate the "Coefficient of variation" for each data of both profile (basal rates, ISF, IC/CarbRatio, CSF) (Coefficient of variation = Standard Deviation / Average value of Ref Profile)...
  - Then we get maximum value (or average) of these 4 Coefficients of variation to have a "Distance"
  - See Excel file for an example of calculation
- If autotune result is perfect, distance is 0
- If output profile is better than Pumpprofile, distance between result and Reference Profile is smaller
  - The more the distance is reduced, the more autotune algorythm is efficient
- We can also run several time Autotune algorythm with the same dataset (for treatment and Bg values), and with new calculated profile



## 3rd make several datasets to be sure that autotune algorythm help to converge to Ref Profile

- What is the good value (in measured in "Distance reduction") to decide that an autotune algorythm is safe?
- build very sensitive data sets, very resistant datasets
- build datasets with long or short distance (in both direction, higher or lower) between pumpprofile and ref profile
- Test Autotune algorithm when PumpProfile is equal Reference Profile (a perfect autotune algorythm should change nothing, but we can accept a result that remains very close...)
- I think it could be great if we can build "Standard Datasets" for full validation of autotune algorythm (shared between OAPS and AAPS)