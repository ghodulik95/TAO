#
# This is a Shiny web application. You can run the application by clicking
# the 'Run App' button above.
#
# Find out more about building applications with Shiny here:
#
#    http://shiny.rstudio.com/
#

library(shiny)

library(reticulate)
library(dplyr)
library(ggplot2)
library(data.table)
library(stringr)
library(gridExtra)
library(ggpubr)


PYTHON_DEPENDENCIES = c('numpy','pandas')

update_ui <- function(outputV, percCumulativeInfections_in = "1", conditions = "{}", data_set = "") {
    
    py_run_string(paste("get_tao_scores(", percCumulativeInfections_in, ",", conditions, ", '", data_set, "')", sep=''))
  
    df <- data.frame(py$df, stringsAsFactors = TRUE)
    df$percCumulativeInfections <- df$cumulativeInfections / 3000
    df <- df %>% dplyr::filter(percCumulativeInfections <= as.numeric(percCumulativeInfections_in))
    df$sMaskCompliance <- factor(df$sMaskCompliance, levels=c("VLOW", "LOW", "HIGH"))
    df$sPhysicalDistancingCompliance <- factor(df$sPhysicalDistancingCompliance, levels=c("VLOW", "LOW", "HIGH"))
    df$percVaccinated <- factor(df$percVaccinated, levels=c(0.0, 0.05, 0.10, 0.25, 0.4, 0.55, 0.7, 0.85))
    df$ctProtocol <- factor(df$ctProtocol, levels=c(1,2,3))
    df$dailyTests <- ifelse(df$dailyTests == 100, "3.3%", ifelse(df$dailyTests == 200, "6.6%", "9.9%"))
    df$dailyTests <- factor(df$dailyTests, levels=c("3.3%","6.6%","9.9%"))
    
    percVaccinated <- ggplot(df, aes(y = percCumulativeInfections, x = percVaccinated)) + 
        geom_boxplot()
        
    maskAllN95 <- ggplot(df, aes(y = percCumulativeInfections, x = maskAllN95)) +
        geom_boxplot()
        
    sMaskCompliance <- ggplot(df, aes(y = percCumulativeInfections, x = sMaskCompliance)) +
        geom_boxplot()
        
    sPhysDistCompliance <- ggplot(df, aes(y = percCumulativeInfections, x = sPhysicalDistancingCompliance)) +
        geom_boxplot()
        
    fCompliance <- ggplot(df, aes(y = percCumulativeInfections, x = fCompliance)) +
        geom_boxplot()
        
    ctProtocol <- ggplot(df, aes(y = percCumulativeInfections, x = ctProtocol)) +
        geom_boxplot()
        
    parties <- ggplot(df, aes(y = percCumulativeInfections, x = parties)) +
        geom_boxplot()
        
    closeFitness <- ggplot(df, aes(y = percCumulativeInfections, x = closeFitness)) +
        geom_boxplot()
        
    hybridClasses <- ggplot(df, aes(y = percCumulativeInfections, x = hybridClasses)) +
        geom_boxplot()
        
    dailyTests <- ggplot(df, aes(y = percCumulativeInfections, x = dailyTests)) +
        geom_boxplot()
        
    cancelSports <- ggplot(df, aes(y = percCumulativeInfections, x = cancelSports)) +
        geom_boxplot()
         
    positives_df <- as.data.frame(reticulate::py$positives_df)
    if (nrow(positives_df) == 0) {
        positives_df <- data.frame('Interventions'=c("All data under cutoff"))
    } else {
        positives_df <- positives_df %>% mutate_if(is.numeric, ~round(., 3))
    }
    negatives_df <- as.data.frame(reticulate::py$negatives_df)
    if (nrow(negatives_df) == 0) {
        negatives_df <- data.frame('Interventions'=c("All data over cutoff"))
    } else {
        negatives_df <- negatives_df %>% mutate_if(is.numeric, ~round(., 3))
    }
    
    print(
        grid.arrange(
            percVaccinated, maskAllN95, sMaskCompliance, sPhysDistCompliance, fCompliance, ctProtocol,
            parties, closeFitness, dailyTests, cancelSports,
            tableGrob(positives_df),
            tableGrob(negatives_df),
            hybridClasses,
            text_grob(paste(nrow(df), " runs below\ncutoff", sep='')),
            layout_matrix = rbind(
                c(14, 11, 11, 11, 11, 1, 1, 2, 2, 3, 3),
                c(NA, 11, 11, 11, 11, 4, 4, 5, 5, 6, 6),
                c(NA, 12, 12, 12, 12, 7, 7, 8, 8, 9, 9),
                c(NA, 12, 12, 12, 12, 10, 10, 13, 13, NA, NA)
            )
        )
    )
}


shinyApp(
    ui = 
        fluidPage(
            sidebarLayout(
                sidebarPanel(
                    h4("Interactive TAO parameter exploration"),
                    h6("Use the slider to filter out parameter runs which resulted in a % agents infected above the slider value. 
                            The data tables will update to indicate which parameter settings contributed most strongly to the filtration.
                            Then, check off interventions to filter only on those parameter settings. See how different parameter settings may
                            acheive the same average number of infections."),
                    h6("Please note that starting this app may be slow, but it should speed up as you use it. It may take about 20s for the first plots to appear."),
                    sliderInput("obs",
                            "Maximum % infected at semester end:",
                            min = 0,
                            max = 0.6,
                            value = 0.6),
                            selectInput("data_file", "Data set", c("With vaccines" = "vaccines", "Without vaccines" = "noVaccines")),
                            checkboxGroupInput("conditions", "Filters:",
                                              c(    
                                                  "Vaccinated: 0%" =   "percVaccinated_0.0" ,  
                                                  "Vacinnated: 5%" =  "percVaccinated_0.05" ,  
                                                  "Vaccinated: 10%" = "percVaccinated_0.1" ,  
                                                  "Vaccinated: 25%" =    "percVaccinated_0.25" ,  
                                                  "Vaccinated: 40%" =   "percVaccinated_0.4" , 
                                                  "Vaccinated: 55%" =   "percVaccinated_0.55" ,  
                                                  "Vaccinated: 70%" = "percVaccinated_0.7" ,   
                                                  "Vaccinated: 85%" =   "percVaccinated_0.85" ,  
                                                  "Student Isolation Complaince: High" =   "sIsolationCompliance_\"HIGH\"" ,  
                                                  "Student Isolation Complaince: Low" =    "sIsolationCompliance_\"LOW\"" , 
                                                  "Student Contact Tracing compliance: High" =   "sIsolationCTCompliance_\"HIGH\"" ,  
                                                  "Student Contact Tracing compliance: Low" =  "sIsolationCTCompliance_\"LOW\"" ,  
                                                  "Student Mask wearing compliance: High" =  "sMaskCompliance_\"HIGH\"" ,  
                                                  "Student Mask wearing compliance: Low" = "sMaskCompliance_\"LOW\"" , 
                                                  "Student Mask weearing compliance: Very Low" = "sMaskCompliance_\"VLOW\"" ,  
                                                  "Student distancing: High" = "sPhysicalDistancingCompliance_\"HIGH\"" ,  
                                                  "Student distancing: Low" = "sPhysicalDistancingCompliance_\"LOW\"" , 
                                                  "Student distancing: Very low" ="sPhysicalDistancingCompliance_\"VLOW\"" ,
                                                  "Mask types: Mixed" = "maskAllN95_\"NO\"" ,   
                                                  "Mask types: All N95" =     "maskAllN95_\"YES\"" , 
                                                  "Sports events: allowed" =  "cancelSports_\"NO\"", 
                                                "Sports events: cancelled" =    "cancelSports_\"YES\"" ,  
                                                "Fitness center: open" =    "closeFitness_\"NO\"" ,  
                                                "Fitness center: closed" =    "closeFitness_\"YES\"" ,  
                                                "Contact Tracing 1: best practice" =  "ctProtocol_1" ,   
                                                "Contact Tracing 2: conserve tests" =     "ctProtocol_2" , 
                                                "Contact Tracing 3: higher QOL best practice" =      "ctProtocol_3" , 
                                                "3.3% tested per day" =  "dailyTests_100" ,  
                                                "6.6% tested per day" =   "dailyTests_200" , 
                                                "9.9% tested per day" =  "dailyTests_300" ,  
                                                "High faculty & staff compliance" =   "fCompliance_\"HIGH\"" ,  
                                                "Low faculty & staff compliance" =   "fCompliance_\"LOW\"" ,  
                                                "Normal class model" =  "hybridClasses_\"NO\"" ,   
                                                "Hybrid class model" =  "hybridClasses_\"YES\"" ,  
                                                "Parties: many" =   "parties_\"MANY\"" ,   
                                                "Parties: None" = "parties_\"NONE\"" ,   
                                                "Parties: some" ="parties_\"SOME\"" ,  
                                                "Student-facing staff test priority: Normal" =  "studentFacingStaffTestingBoost_1" ,  
                                                "Student-facing staff test priority: High" = "studentFacingStaffTestingBoost_5" )),
             width = 2
                ),
                mainPanel(
                    plotOutput('outputGrid')
                )
            )
        )
    ,
    server = function(input, output) {
        # ------------------ App virtualenv setup (Do not edit) ------------------- #
        
        virtualenv_dir = Sys.getenv('VIRTUALENV_NAME')
        python_path = Sys.getenv('PYTHON_PATH')
        
        # Create virtual env and install dependencies
        reticulate::virtualenv_create(envname = virtualenv_dir, python = python_path)
        reticulate::virtualenv_install(virtualenv_dir, packages = PYTHON_DEPENDENCIES, ignore_installed=TRUE)
        reticulate::use_virtualenv(virtualenv_dir, required = T)
        
        # ------------------ App server logic (Edit anything below) --------------- #
        
        reticulate::source_python("TAO_data_python.py")
        
        output$txt <- renderText({
            icons <- paste(input$conditions, collapse = ", ")
            paste("You chose", icons)
        })
        
        output$outputGrid <- renderPlot({
            conditions_list <- input$conditions
            conditions = "{"
            if (length(conditions_list) > 0) {
                first <- TRUE
                for (val in conditions_list) {
                    if (first == FALSE) {
                        conditions <- paste(conditions, " , ", sep="")
                    }
                    v <- str_split(val, "_", n = 2)
                    conditions <- paste(conditions, "'", v[[1]][1], "' : ", v[[1]][2], sep="")
                    first <- FALSE
                }
            }
            conditions <- paste(conditions, "}", sep="")
            update_ui(output, input$obs, conditions, input$data_file)
        }, height = 1000)
    }
)
